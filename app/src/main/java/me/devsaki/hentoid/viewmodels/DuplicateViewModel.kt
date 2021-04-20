package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.workers.DuplicateDetectorWorker
import me.devsaki.hentoid.workers.data.DuplicateData


class DuplicateViewModel(application: Application, val dao: CollectionDAO) : AndroidViewModel(application) {

    val allDuplicates = MutableLiveData<List<DuplicateEntry>>()
    val selectedDuplicates = MutableLiveData<List<DuplicateEntry>>()


    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
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

        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueue(OneTimeWorkRequest.Builder(DuplicateDetectorWorker::class.java).setInputData(builder.data).build())
    }

    fun setContent(content: Content) {
        selectedDuplicates.postValue(allDuplicates.value?.filter { it.reference == content.id })
    }
}