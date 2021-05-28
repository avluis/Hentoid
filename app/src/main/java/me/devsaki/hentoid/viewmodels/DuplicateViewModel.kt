package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consts
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.DuplicatesDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.notification.duplicates.DuplicateNotificationChannel
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.exception.ContentNotRemovedException
import me.devsaki.hentoid.util.exception.FileNotRemovedException
import me.devsaki.hentoid.workers.DuplicateDetectorWorker
import me.devsaki.hentoid.workers.data.DuplicateData
import timber.log.Timber
import java.util.concurrent.TimeUnit


class DuplicateViewModel(
    application: Application,
    private val dao: CollectionDAO,
    private val duplicatesDao: DuplicatesDAO
) : AndroidViewModel(application) {

    // Cleanup for all RxJava calls
    private val compositeDisposable = CompositeDisposable()

    // LiveData for the UI
    val allDuplicates = duplicatesDao.getEntriesLive()
    val selectedDuplicates = MutableLiveData<List<DuplicateEntry>>()
    val firstUse = MutableLiveData<Boolean>()


    override fun onCleared() {
        super.onCleared()
        duplicatesDao.cleanup()
        compositeDisposable.clear()
    }

    fun setFirstUse(value: Boolean) {
        firstUse.postValue(value)
    }

    fun scanForDuplicates(
        useTitle: Boolean,
        useCover: Boolean,
        useArtist: Boolean,
        sameLanguageOnly: Boolean,
        ignoreChapters : Boolean,
        sensitivity: Int
    ) {
        val builder = DuplicateData.Builder()
        builder.setUseTitle(useTitle)
        builder.setUseCover(useCover)
        builder.setUseArtist(useArtist)
        builder.setUseSameLanguage(sameLanguageOnly)
        builder.setIgnoreChapters(ignoreChapters)
        builder.setSensitivity(sensitivity)

        DuplicateNotificationChannel.init(getApplication())
        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueueUniqueWork(
            R.id.duplicate_detector_service.toString(),
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DuplicateDetectorWorker>()
                .setInputData(builder.data)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(Consts.WORK_CLOSEABLE)
                .build()
        )
    }

    fun setContent(content: Content) {
        val selectedDupes = ArrayList(allDuplicates.value?.filter { it.referenceId == content.id })
        // Add reference item on top
        val refEntry = DuplicateEntry(
            content.id,
            content.size,
            content.id,
            content.size,
            2f,
            2f
        ) // Artificially give it a huge score to bring it to the top
        refEntry.referenceContent = content
        refEntry.duplicateContent = content
        selectedDupes.add(0, refEntry)
        selectedDuplicates.postValue(selectedDupes)
    }

    fun setBookChoice(content: Content, choice: Boolean) {
        val selectedDupes = ArrayList(selectedDuplicates.value)
        for (dupe in selectedDupes) {
            if (dupe.duplicateId == content.id) dupe.keep = choice
        }
        selectedDuplicates.postValue(selectedDupes)
    }

    fun applyChoices(onComplete: Runnable) {
        val selectedDupes = ArrayList(selectedDuplicates.value)

        compositeDisposable.add(
            Observable.fromIterable(selectedDupes)
                .observeOn(Schedulers.io())
                .map {
                    if (!it.keep) doRemove(it.duplicateId)
                    it
                }
                .doOnNext {
                    // Update UI
                    if (!it.keep) {
                        val newList = selectedDupes.toMutableList()
                        newList.remove(it)
                        selectedDuplicates.postValue(newList) // Post a copy so that we don't modify the collection we're looping on
                    }
                    if (it.titleScore <= 1f) // Just don't try to delete the fake reference entry that has been put there for display
                        duplicatesDao.delete(it)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = {
                        // Already done on IO thread by doOnNext
                    },
                    onError = { t -> Timber.w(t) },
                    onComplete = {
                        onComplete.run()
                    }
                )
        )
    }

    @Throws(ContentNotRemovedException::class)
    private fun doRemove(contentId: Long) {
        Helper.assertNonUiThread()
        // Remove content altogether from the DB (including queue)
        val content: Content = dao.selectContent(contentId) ?: return
        try {
            ContentHelper.removeQueuedContent(getApplication(), dao, content)
        } catch (e: ContentNotRemovedException) {
            // Don't throw the exception if we can't remove something that isn't there
            if (!(e is FileNotRemovedException && content.storageUri.isEmpty())) throw e
        }
    }
}