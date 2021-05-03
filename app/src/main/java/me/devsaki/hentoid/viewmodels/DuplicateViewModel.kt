package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consts
import me.devsaki.hentoid.database.DuplicatesDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.notification.duplicates.DuplicateNotificationChannel
import me.devsaki.hentoid.workers.DuplicateDetectorWorker
import me.devsaki.hentoid.workers.data.DuplicateData
import java.util.concurrent.TimeUnit


class DuplicateViewModel(application: Application, private val duplicatesDao: DuplicatesDAO) : AndroidViewModel(application) {

    val allDuplicates = duplicatesDao.getEntriesLive()
    val selectedDuplicates = MutableLiveData<List<DuplicateEntry>>()
    val firstUse = MutableLiveData<Boolean>()


    override fun onCleared() {
        super.onCleared()
        duplicatesDao.cleanup()
    }

    fun scanForDuplicates(
            useTitle: Boolean,
            useCover: Boolean,
            useArtist: Boolean,
            sameLanguageOnly: Boolean,
            sensitivity: Int
    ) {
        val builder = DuplicateData.Builder()
        builder.setUseTitle(useTitle)
        builder.setUseCover(useCover)
        builder.setUseArtist(useArtist)
        builder.setUseSameLanguage(sameLanguageOnly)
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
                                TimeUnit.MILLISECONDS)
                        .addTag(Consts.WORK_CLOSEABLE)
                        .build()
        )
    }

    fun setContent(content: Content) {
        val selectedDupes = ArrayList(allDuplicates.value?.filter { it.referenceId == content.id })
        // Add reference item on top
        val refEntry = DuplicateEntry(content.id, content.size, content.id, 2f, 2f) // Artificially give it a huge score to bring it to the top
        refEntry.referenceContent = content
        refEntry.duplicateContent = content
        selectedDupes.add(0, refEntry)
        selectedDuplicates.postValue(selectedDupes)
    }

    fun setBookChoice(content: Content, choice: Boolean?) {
        val selectedDupes = ArrayList(selectedDuplicates.value)
        for (dupe in selectedDupes) {
            if (dupe.duplicateId == content.id) dupe.keep = choice
        }
        selectedDuplicates.postValue(selectedDupes)
    }

    fun setFirstUse(value: Boolean) {
        firstUse.postValue(value)
    }
}