package me.devsaki.hentoid.core

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
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