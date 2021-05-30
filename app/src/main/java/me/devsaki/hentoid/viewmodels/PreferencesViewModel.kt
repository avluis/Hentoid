package me.devsaki.hentoid.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposables
import io.reactivex.schedulers.Schedulers
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.FileHelper
import me.devsaki.hentoid.util.ImageHelper
import me.devsaki.hentoid.util.exception.ContentNotRemovedException
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.File

class PreferencesViewModel(application: Application, val dao: CollectionDAO) :
    AndroidViewModel(application) {

    private var deleteDisposable = Disposables.empty()

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
        val context = getApplication<Application>().applicationContext
        var nbDeleted = 0

        deleteDisposable = Observable.fromIterable(items)
            .observeOn(Schedulers.io())
            .map { c: Content -> this.deleteItem(context, c) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    nbDeleted++
                    this.onDeleteProgress(nbDeleted, items.size)
                },
                { t: Throwable? -> Timber.w(t) },
                { this.onDeleteComplete(nbDeleted, items.size) }
            )
    }

    @Throws(ContentNotRemovedException::class)
    private fun deleteItem(context: Context, c: Content) {
        ContentHelper.removeContent(context, dao, c)
    }

    private fun onDeleteProgress(num: Int, max: Int) {
        EventBus.getDefault()
            .post(ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.generic_delete, 0, num, 0, max))
    }

    private fun onDeleteComplete(num: Int, max: Int) {
        deleteDisposable.dispose()
        EventBus.getDefault()
            .post(ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.generic_delete, 0, num, 0, max))
    }
}