package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.util.Preferences

/**
 * Responsible for creating ViewModels and supplying their dependencies
 */
class ViewModelFactory(val application: Application): ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return when(modelClass) {
            LibraryViewModel::class.java -> LibraryViewModel(application, ObjectBoxDAO(application))
            SearchViewModel::class.java -> SearchViewModel(ObjectBoxDAO(application), Preferences.getAttributesSortOrder())
            QueueViewModel::class.java -> QueueViewModel(application, ObjectBoxDAO(application))
            ImageViewerViewModel::class.java -> ImageViewerViewModel(application, ObjectBoxDAO(application))
            else -> throw RuntimeException()
        } as T
    }
}