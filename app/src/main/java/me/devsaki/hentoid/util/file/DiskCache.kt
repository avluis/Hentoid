package me.devsaki.hentoid.util.file

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.util.Helper
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * The app's disk-based cache
 */
object DiskCache {
    private const val FOLDER_NAME = "disk_cache"
    private const val SIZE_LIMIT_DEBUG = 50 * 1024 * 1024 // 50MB
    private const val SIZE_LIMIT_PRODUCTION = 50 * 1024 * 1024 // 50MB
    private val SIZE_LIMIT = if (BuildConfig.DEBUG) SIZE_LIMIT_DEBUG else SIZE_LIMIT_PRODUCTION

    // Key = URL
    // Value.left = timestamp of last access
    // Value.right = Uri of file
    private val entries = HashMap<String, Pair<Long, Uri>>()
    private var lastPurge = Instant.now().toEpochMilli()

    private lateinit var folder: File

    fun init(context: Context) {
        Helper.assertNonUiThread()
        folder = FileHelper.getOrCreateCacheFolder(context, FOLDER_NAME)
            ?: throw IOException("Couldn't initialize cache folder $FOLDER_NAME")
        if (!folder.deleteRecursively()) Timber.w("Couldn't empty cache folder $FOLDER_NAME")
        folder = FileHelper.getOrCreateCacheFolder(context, FOLDER_NAME)
            ?: throw IOException("Couldn't initialize cache folder $FOLDER_NAME")
        synchronized(entries) {
            entries.clear()
        }
    }

    private fun purgeIfNeeded() {
        // Avoid straining the system
        if (Instant.now().toEpochMilli() - lastPurge < 1000) return

        CoroutineScope(Dispatchers.Default).launch {
            val sortedEntries: MutableList<MutableMap.MutableEntry<String, Pair<Long, Uri>>> by lazy {
                entries.entries.sortedBy { e -> e.value.first }.toMutableList()
            }
            withContext(Dispatchers.IO) {
                var storageTaken = getUsedStorage()
                while (storageTaken > SIZE_LIMIT) {
                    if (sortedEntries.isEmpty()) break
                    val oldestEntry = sortedEntries[0]
                    synchronized(entries) {
                        Timber.d("Disk cache : removing %s", oldestEntry.key)
                        entries.remove(oldestEntry.key)
                    }
                    FileHelper.legacyFileFromUri(oldestEntry.value.second)?.let {
                        storageTaken -= it.length()
                        it.delete()
                    }
                    sortedEntries.removeAt(0)
                }
                lastPurge = Instant.now().toEpochMilli()
            }
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
            // Change timestamp of existing entry
            entries[key] = Pair(Instant.now().toEpochMilli(), entry.second)
            return entry.second
        }
    }
}