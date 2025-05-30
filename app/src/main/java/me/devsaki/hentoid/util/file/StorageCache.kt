package me.devsaki.hentoid.util.file

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.util.assertNonUiThread
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * The app's fixed-size storage-based cache
 * NB : Don't use this class if the "cache" has no size limit; just save stuff to context.filesDir
 */
object StorageCache {

    // Key = Cache ID
    // Value = Location of the cache folder
    private val folder = HashMap<String, File>()

    // Key = Cache ID
    // Value = Is the cache permanent ?
    // NB : Non-permanent cache gets cleared during init; permanent is not
    private val isPermanent = HashMap<String, Boolean>()

    // Key = Cache ID
    // Value = Maximum cache size (bytes))
    private val sizeLimit = HashMap<String, Int>()

    // Key = Cache ID
    // Value = Cache
    //   Key = Identifier of the file to access (formatted by the caller)
    //   Value.first = Timestamp of last access
    //   Value.second = Uri of cached file
    private val entries = HashMap<String, HashMap<String, Pair<Long, Uri>>>()

    // Key = Cache ID
    // Value = Timestamp for the last purge
    private val lastPurge = HashMap<String, Long>()

    // Key = Cache ID
    // Value = Delegate to broadcast cleanup events
    private var cleanupObservers = HashMap<String, HashMap<String, () -> Unit>>()


    fun init(
        context: Context,
        cacheId: String,
        limit: Int,
        permanent: Boolean = false,
        forceClear: Boolean = false
    ) {
        assertNonUiThread()

        var theFolder = getOrCreateCacheFolder(context, cacheId)
            ?: throw IOException("Couldn't initialize cache folder $cacheId")
        if (!permanent || forceClear) {
            if (!theFolder.deleteRecursively()) Timber.w("Couldn't empty cache folder $cacheId")
            theFolder = getOrCreateCacheFolder(context, cacheId)
                ?: throw IOException("Couldn't initialize cache folder $cacheId")
        }
        folder[cacheId] = theFolder

        isPermanent[cacheId] = permanent
        sizeLimit[cacheId] = limit

        val now = Instant.now().toEpochMilli()
        lastPurge[cacheId] = now

        val theEntries = HashMap<String, Pair<Long, Uri>>()
        entries[cacheId] = theEntries
        if (permanent && !forceClear) {
            theFolder.listFiles()?.filterNotNull()?.forEach {
                theEntries[it.name] = Pair(now, it.toUri())
            }
        }

        // Call existing observers as initializing an existing cache cleans it up
        if (cleanupObservers.containsKey(cacheId))
            cleanupObservers[cacheId] = HashMap<String, () -> Unit>()
        cleanupObservers[cacheId]?.forEach { it.value.invoke() }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun purgeIfNeeded(cacheId: String) {
        // Avoid straining the system
        val now = Instant.now().toEpochMilli()
        if (now - lastPurge[cacheId]!! < 1000) return
        lastPurge[cacheId] = now

        GlobalScope.launch(Dispatchers.Default) {
            val sortedEntries =
                entries[cacheId]!!.entries.sortedBy { it.value.first }.toMutableList()
            withContext(Dispatchers.IO) {
                var storageTaken = getUsedStorage(cacheId)
                val limit = sizeLimit[cacheId]!!
                while (storageTaken > limit) {
                    if (sortedEntries.isEmpty()) break
                    val oldestEntry = sortedEntries[0]
                    synchronized(entries[cacheId]!!) {
                        Timber.d("Storage cache : removing %s", oldestEntry.key)
                        entries[cacheId]!!.remove(oldestEntry.key)
                    }
                    legacyFileFromUri(oldestEntry.value.second)?.let {
                        storageTaken -= it.length()
                        it.delete()
                    }
                    sortedEntries.removeAt(0)
                }
            }
            cleanupObservers[cacheId]?.forEach { it.value.invoke() }
        }
    }

    private fun getUsedStorage(cacheId: String): Long {
        assertNonUiThread()
        var result = 0L
        folder[cacheId]!!.listFiles()?.forEach {
            result += it.length()
        }
        return result
    }

    fun clearAll(context: Context) {
        folder.entries.forEach {
            init(context, it.key, sizeLimit[it.key]!!, isPermanent[it.key]!!, true)
        }
    }

    fun clear(context: Context, cacheId: String) {
        init(context, cacheId, sizeLimit[cacheId]!!, isPermanent[cacheId]!!, true)
    }

    fun createFile(cacheId: String, key: String): Uri {
        assertNonUiThread()
        val targetFile = File(folder[cacheId], key)
        if (!targetFile.exists() && !targetFile.createNewFile()) throw IOException("Couldn't create file for key $key in cache folder $cacheId")
        val result = Uri.fromFile(targetFile)
        synchronized(entries[cacheId]!!) {
            entries[cacheId]!![key] = Pair(Instant.now().toEpochMilli(), result)
        }
        purgeIfNeeded(cacheId)
        return result
    }

    fun getFile(cacheId: String, key: String): Uri? {
        entries[cacheId]?.let {
            synchronized(it) {
                val entry = it[key] ?: return null
                // Change timestamp of existing entry as it has just been asked for
                it[key] = Pair(Instant.now().toEpochMilli(), entry.second)
                return entry.second
            }
        }
        return null
    }

    fun peekFile(cacheId: String, key: String): Boolean {
        return entries[cacheId]?.containsKey(key) == true
    }

    fun createFinder(cacheId: String): (String) -> Uri? {
        return { targetFileName -> getFile(cacheId, targetFileName) }
    }

    fun createCreator(cacheId: String): (String) -> Uri? {
        return { targetFileName ->
            val file = File(createFile(cacheId, targetFileName).path!!)
            if (file.exists() || file.createNewFile()) file.toUri() else null
        }
    }

    fun addCleanupObserver(cacheId: String, key: String, observer: () -> Unit) {
        cleanupObservers[cacheId]?.let {
            it[key] = observer
            Timber.d("Observer added; %d registered", it.size)
        }
    }

    fun removeCleanupObserver(cacheId: String, key: String) {
        cleanupObservers[cacheId]?.let {
            it.remove(key)
            Timber.d("Observer removed; %d registered", it.size)
        }
    }
}