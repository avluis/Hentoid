package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.devsaki.hentoid.database.ObjectBoxDAO
import java.lang.RuntimeException

/**
 * Responsible for creating ViewModels and supplying their dependencies
 */
class ViewModelFactory(val application: Application): ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return when(modelClass) {
            SearchViewModel::class.java -> SearchViewModel(ObjectBoxDAO(application))
            else -> throw RuntimeException()
        } as T
    }
}