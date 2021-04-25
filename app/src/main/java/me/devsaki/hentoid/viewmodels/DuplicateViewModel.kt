package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consts
import me.devsaki.hentoid.database.DuplicatesDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.notification.duplicates.DuplicateNotificationChannel
import me.devsaki.hentoid.workers.DuplicateDetectorWorker
import me.devsaki.hentoid.workers.data.DuplicateData


class DuplicateViewModel(application: Application, private val duplicatesDao: DuplicatesDAO) : AndroidViewModel(application) {

    val allDuplicates = duplicatesDao.getEntriesLive()
    val selectedDuplicates = MutableLiveData<List<DuplicateEntry>>()


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
                        .addTag(Consts.WORK_CLOSEABLE)
                        .build()
        )
    }

    fun setContent(content: Content) {
        selectedDuplicates.postValue(allDuplicates.value?.filter { it.reference == content.id })
    }
}