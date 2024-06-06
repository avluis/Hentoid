package me.devsaki.hentoid.util.file

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.util.Helper
import org.apache.commons.collections4.map.HashedMap
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * The app's disk-based cache
 */
object DiskCache {
    private const val FOLDER_NAME = "disk_cache"
    private const val SIZE_LIMIT_DEBUG = 50 * 1024 * 1024
    private const val SIZE_LIMIT_PRODUCTION = 50 * 1024 * 1024 // 50MB
    private val SIZE_LIMIT = if (BuildConfig.DEBUG) SIZE_LIMIT_DEBUG else SIZE_LIMIT_PRODUCTION

    // Location of the cache folder
    private lateinit var folder: File

    // Key = URL
    // Value.first = timestamp of last access
    // Value.second = Uri of file
    private val entries = HashedMap<String, Pair<Long, Uri>>()

    // Timestamp for the last purge
    private var lastPurge = Instant.now().toEpochMilli()

    // To broadcast cleanup events
    private var cleanupObservers = HashMap<String, () -> Unit>()


    fun init(context: Context) {
        Helper.assertNonUiThread()
        folder = getOrCreateCacheFolder(context, FOLDER_NAME)
            ?: throw IOException("Couldn't initialize cache folder $FOLDER_NAME")
        if (!folder.deleteRecursively()) Timber.w("Couldn't empty cache folder $FOLDER_NAME")
        folder = getOrCreateCacheFolder(context, FOLDER_NAME)
            ?: throw IOException("Couldn't initialize cache folder $FOLDER_NAME")
        synchronized(entries) {
            entries.clear()
        }
    }

    private fun purgeIfNeeded() {
        // Avoid straining the system
        if (Instant.now().toEpochMilli() - lastPurge < 1000) return
        lastPurge = Instant.now().toEpochMilli()

        CoroutineScope(Dispatchers.Default).launch {
            val sortedEntries = entries.entries.sortedBy { it.value.first }.toMutableList()
            withContext(Dispatchers.IO) {
                var storageTaken = getUsedStorage()
                while (storageTaken > SIZE_LIMIT) {
                    if (sortedEntries.isEmpty()) break
                    val oldestEntry = sortedEntries[0]
                    synchronized(entries) {
                        Timber.d("Disk cache : removing %s", oldestEntry.key)
                        entries.remove(oldestEntry.key)
                    }
                    legacyFileFromUri(oldestEntry.value.second)?.let {
                        storageTaken -= it.length()
                        it.delete()
                    }
                    sortedEntries.removeAt(0)
                }
            }
            cleanupObservers.values.forEach { it.invoke() }
        }
    }

    private fun getUsedStorage(): Long {
        Helper.assertNonUiThread()
        var result = 0L
        folder.listFiles()?.forEach {
            result += it.length()
        }
        return result
    }

    fun createFile(key: String): Uri {
        Helper.assertNonUiThread()
        val targetFile = File(folder, Helper.hash64(key.encodeToByteArray()).toString())
        if (!targetFile.exists() && !targetFile.createNewFile()) throw IOException("Couldn't create file for key $key in cache folder $FOLDER_NAME")
        val result = Uri.fromFile(targetFile)
        synchronized(entries) {
            entries[key] = Pair(Instant.now().toEpochMilli(), result)
        }
        purgeIfNeeded()
        return result
    }

    fun getFile(key: String): Uri? {
        synchronized(entries) {
            val entry = entries[key] ?: return null
            // Change timestamp of existing entry as it has just been asked for
            entries[key] = Pair(Instant.now().toEpochMilli(), entry.second)
            return entry.second
        }
    }

    fun peekFile(key: String): Boolean {
        return entries.containsKey(key)
    }

    fun addCleanupObserver(key: String, observer: () -> Unit) {
        cleanupObservers[key] = observer
        Timber.d("Observer added; %d registered", cleanupObservers.size)
    }

    fun removeCleanupObserver(key: String) {
        cleanupObservers.remove(key)
        Timber.d("Observer removed; %d registered", cleanupObservers.size)
    }
}