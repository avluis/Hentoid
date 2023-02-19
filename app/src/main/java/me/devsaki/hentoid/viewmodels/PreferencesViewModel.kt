package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.workers.DeleteWorker
import me.devsaki.hentoid.workers.data.DeleteData
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.atomic.AtomicInteger

class PreferencesViewModel(application: Application, val dao: CollectionDAO) :
    AndroidViewModel(application) {

    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
    }

    fun detach(location: StorageLocation) {
        when (location) {
            StorageLocation.PRIMARY_1,
            StorageLocation.PRIMARY_2 -> {
                ContentHelper.detachAllPrimaryContent(
                    dao,
                    location
                )
            }

            StorageLocation.EXTERNAL -> {
                ContentHelper.detachAllExternalContent(
                    getApplication<Application>().applicationContext,
                    dao
                )
                dao.cleanupOrphanAttributes()
            }

            else -> {
                // Nothing
            }
        }
        Preferences.setStorageUri(location, "")
    }

    suspend fun merge2to1(nbBooks: Int) {
        val nbOK = AtomicInteger(0)
        val nbKO = AtomicInteger(0)
        withContext(Dispatchers.IO) {
            val ids = ArrayList<Long>()
            dao.streamAllInternalBooks(
                ContentHelper.getPathRoot(StorageLocation.PRIMARY_2), false
            ) { c -> ids.add(c.id) }
            ids.forEach {
                mergeTo1(it, nbOK, nbKO, nbBooks)
            }
            EventBus.getDefault().postSticky(
                ProcessEvent(
                    ProcessEvent.EventType.COMPLETE,
                    R.id.generic_progress,
                    0,
                    nbOK.get(),
                    nbKO.get(),
                    nbBooks
                )
            )
        }
    }

    private suspend fun mergeTo1(
        contentId: Long,
        nbOK: AtomicInteger,
        nbKO: AtomicInteger,
        nbBooks: Int
    ) {
        var success = false
        withContext(Dispatchers.IO) {
            val content = dao.selectContent(contentId)

            content?.let { c ->
                // Create book folder in Location 1
                val targetFolder = ContentHelper.getOrCreateContentDownloadDir(
                    getApplication(),
                    c,
                    StorageLocation.PRIMARY_1,
                    false
                )
                if (targetFolder != null) {
                    // Transfer files
                    val sourceFolder =
                        FileHelper.getDocumentFromTreeUriString(getApplication(), c.storageUri)
                    if (sourceFolder != null) {
                        val files = FileHelper.listFiles(getApplication(), sourceFolder, null)
                        // TODO secondary progress for pages
                        files.forEach { it1 ->
                            val newUri = FileHelper.copyFile(
                                getApplication(),
                                it1.uri,
                                targetFolder.uri,
                                StringHelper.protect(it1.type),
                                StringHelper.protect(it1.name)
                            )
                            c.imageFiles?.forEach { it2 ->
                                if (it1.uri.toString() == it2.fileUri) {
                                    it2.fileUri = newUri.toString()
                                }
                            }
                        }
                        // Update Content Uris
                        c.storageUri = targetFolder.uri.toString()
                        dao.insertContentCore(c)
                        c.imageFiles?.let {
                            dao.insertImageFiles(it)
                        }
                        success = true

                        // Remove files from initial location
                        sourceFolder.delete()
                    }
                }
            }
            if (success) nbOK.incrementAndGet() else nbKO.incrementAndGet()

            EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.EventType.PROGRESS,
                    R.id.generic_progress,
                    0,
                    nbOK.get(),
                    nbKO.get(),
                    nbBooks
                )
            )
        }
    }
}