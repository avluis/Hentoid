package me.devsaki.hentoid.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

abstract class BaseDialogFragment<T> : DialogFragment() {

    companion object {
        private const val PARENT_IS_ACTIVITY = "PARENT_IS_ACTIVITY"

        @JvmStatic
        protected fun invoke(
            activity: FragmentActivity,
            dialog: DialogFragment,
            args: Bundle? = null,
            isCancelable: Boolean = true
        ): DialogFragment {
            return invoke(activity.supportFragmentManager, dialog, args, isCancelable, true)
        }

        @JvmStatic
        protected fun invoke(
            fragment: Fragment,
            dialog: DialogFragment,
            args: Bundle? = null,
            isCancelable: Boolean = true,
            parentIsActivity: Boolean = false,
        ): DialogFragment {
            return invoke(
                fragment.childFragmentManager, // Parent ?
                dialog,
                args,
                isCancelable,
                parentIsActivity
            )
        }

        private fun invoke(
            fragmentManager: FragmentManager,
            dialog: DialogFragment,
            args: Bundle?,
            isCancelable: Boolean,
            parentIsActivity: Boolean,
        ): DialogFragment {
            val argz = args ?: Bundle()
            argz.putBoolean(PARENT_IS_ACTIVITY, parentIsActivity)
            dialog.arguments = argz
            dialog.isCancelable = isCancelable
            dialog.show(fragmentManager, null)
            return dialog
        }
    }

    // VARIABLES
    var parent: T? = null
        private set

    @Suppress("UNCHECKED_CAST") // Can't check against a generic type because of Kotlin type erasure
    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        parent = if (requireArguments().getBoolean(PARENT_IS_ACTIVITY, false)) activity as T?
        else parentFragment as T?
    }

    override fun onDestroy() {
        parent = null
        super.onDestroy()
    }
}