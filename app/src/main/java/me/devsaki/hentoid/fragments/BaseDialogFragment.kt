package me.devsaki.hentoid.fragments

import androidx.fragment.app.DialogFragment

abstract class BaseDialogFragment<T : Any> : DialogFragment() {

    @Suppress("UNCHECKED_CAST")
    var parent: T? = null
        get() = parentFragment as? T? ?: activity as? T?
        private set
}