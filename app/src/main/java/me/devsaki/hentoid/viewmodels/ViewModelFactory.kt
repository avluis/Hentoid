package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.devsaki.hentoid.database.DuplicatesDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.util.Settings

/**
 * Responsible for creating ViewModels and supplying their dependencies
 */
@Suppress("UNCHECKED_CAST")
class ViewModelFactory(val application: Application) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            LibraryViewModel::class.java -> LibraryViewModel(
                application,
                ObjectBoxDAO()
            )

            SearchViewModel::class.java -> SearchViewModel(
                application,
                ObjectBoxDAO(),
                Settings.searchAttributesSortOrder
            )

            QueueViewModel::class.java -> QueueViewModel(application, ObjectBoxDAO())
            ReaderViewModel::class.java -> ReaderViewModel(application, ObjectBoxDAO())

            PreferencesViewModel::class.java -> PreferencesViewModel(
                application,
                ObjectBoxDAO()
            )

            DuplicateViewModel::class.java -> DuplicateViewModel(
                application,
                ObjectBoxDAO(),
                DuplicatesDAO()
            )

            MetadataEditViewModel::class.java -> MetadataEditViewModel(
                application,
                ObjectBoxDAO()
            )

            RulesEditViewModel::class.java -> RulesEditViewModel(
                application,
                ObjectBoxDAO()
            )

            else -> throw RuntimeException()
        } as T
    }
}