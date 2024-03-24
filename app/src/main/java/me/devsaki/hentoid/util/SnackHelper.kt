package me.devsaki.hentoid.util

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar


fun <T : AppCompatActivity> T.snack(@StringRes resId: Int) {
    snack(resources.getString(resId), true)
}

fun <T : AppCompatActivity> T.shortSnack(@StringRes resId: Int) {
    snack(resources.getString(resId), false)
}

fun <T : AppCompatActivity> T.snack(msg: String) {
    snack(msg, true)
}

fun <T : AppCompatActivity> T.shortSnack(msg: String) {
    snack(msg, false)
}

private fun <T : AppCompatActivity> T.snack(msg: String, long: Boolean = false) {
    this.window.decorView.let {
        Snackbar.make(
            it, msg,
            if (long) BaseTransientBottomBar.LENGTH_LONG else BaseTransientBottomBar.LENGTH_SHORT
        ).show()
    }
}

fun <T : Fragment> T.snack(@StringRes resId: Int, long: Boolean = true) {
    snack(resources.getString(resId), long)
}

fun <T : Fragment> T.snack(msg: String, long: Boolean = true) {
    this.view?.let {
        Snackbar.make(
            it,
            msg,
            if (long) BaseTransientBottomBar.LENGTH_LONG else BaseTransientBottomBar.LENGTH_SHORT
        ).show()
    }
}