package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.devsaki.hentoid.database.DuplicatesDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.util.Preferences

/**
 * Responsible for creating ViewModels and supplying their dependencies
 */
@Suppress("UNCHECKED_CAST")
class ViewModelFactory(val application: Application) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            LibraryViewModel::class.java -> LibraryViewModel(application, ObjectBoxDAO(application))
            SearchViewModel::class.java -> SearchViewModel(ObjectBoxDAO(application), Preferences.getSearchAttributesSortOrder())
            QueueViewModelK::class.java -> QueueViewModelK(application, ObjectBoxDAO(application))
            ReaderViewModel::class.java -> ReaderViewModel(application,ObjectBoxDAO(application))
            PreferencesViewModel::class.java -> PreferencesViewModel(application, ObjectBoxDAO(application))
            DuplicateViewModel::class.java -> DuplicateViewModel(application, ObjectBoxDAO(application), DuplicatesDAO(application))
            MetadataEditViewModel::class.java -> MetadataEditViewModel(application, ObjectBoxDAO(application))
            RulesEditViewModel::class.java -> RulesEditViewModel(application, ObjectBoxDAO(application))
            else -> throw RuntimeException()
        } as T
    }
}