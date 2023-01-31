package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.fragments.preferences.LibRefreshDialogFragment.Location
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.workers.DeleteWorker
import me.devsaki.hentoid.workers.data.DeleteData

class PreferencesViewModel(application: Application, val dao: CollectionDAO) :
    AndroidViewModel(application) {

    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
    }

    fun detach(location: Location) {
        when (location) {
            Location.PRIMARY_1,
            Location.PRIMARY_2 -> {
                ContentHelper.detachAllPrimaryContent(
                    dao,
                    location
                )
            }

            Location.EXTERNAL -> {
                ContentHelper.detachAllExternalContent(
                    getApplication<Application>().applicationContext,
                    dao
                )
            }

            else -> {
                // Nothing
            }
        }
        Preferences.setStorageUri(location, "")
    }

    fun deleteAllItemsExceptFavourites() {
        val builder = DeleteData.Builder()
        builder.setDeleteAllContentExceptFavs(true)

        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueueUniqueWork(
            R.id.delete_service_delete.toString(),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DeleteWorker>()
                .setInputData(builder.data)
                .build()
        )
    }
}