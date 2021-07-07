package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.annimon.stream.Stream
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.util.FileHelper
import me.devsaki.hentoid.util.ImageHelper
import me.devsaki.hentoid.workers.ContentDownloadWorker
import me.devsaki.hentoid.workers.DeleteWorker
import me.devsaki.hentoid.workers.data.DeleteData
import java.io.File

class PreferencesViewModel(application: Application, val dao: CollectionDAO) :
    AndroidViewModel(application) {

    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
    }

    fun removeAllExternalContent() {
        val context = getApplication<Application>().applicationContext

        // Remove all external books from DB
        // NB : do NOT use ContentHelper.removeContent as it would remove files too
        // here we just want to remove DB entries without removing files
        dao.deleteAllExternalBooks()

        // Remove all images stored in the app's persistent folder (archive covers)
        val appFolder: File = context.filesDir
        val images =
            appFolder.listFiles { _: File?, name: String? -> ImageHelper.isSupportedImage(name!!) }
        if (images != null) for (f in images) FileHelper.removeFile(f)
    }

    fun deleteItems(items: List<Content>) {
        val builder = DeleteData.Builder()
        if (items.isNotEmpty()) builder.setContentIds(
            Stream.of(items).map { obj: Content -> obj.id }.toList()
        )

        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueueUniqueWork(
            R.id.delete_service.toString(),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DeleteWorker>()
                .setInputData(builder.data)
                .build()
        )
    }
}