package me.devsaki.hentoid.core

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity

fun FragmentActivity.show(dialogFragment: DialogFragment) {
    dialogFragment.show(supportFragmentManager, null)
}
