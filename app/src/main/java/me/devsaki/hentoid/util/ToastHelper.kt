package me.devsaki.hentoid.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import timber.log.Timber

fun Context.toast(message: String) {
    toast(message, Toast.LENGTH_SHORT)
}

fun Context.toast(@StringRes resource: Int, vararg args: Any) {
    toastShort(resource, *args)
}

fun <T : Fragment> T.toast(@StringRes resource: Int, vararg args: Any) {
    this.view?.context?.apply {
        toastShort(resource, *args)
    }
}

fun Context.toastShort(@StringRes resource: Int, vararg args: Any) {
    toast(resource, Toast.LENGTH_SHORT, *args)
}

fun <T : Fragment> T.toastShort(@StringRes resource: Int, vararg args: Any) {
    this.view?.context?.apply {
        toastShort(resource, *args)
    }
}

fun Context.toastLong(@StringRes resource: Int, vararg args: Any) {
    toast(resource, Toast.LENGTH_LONG, *args)
}

fun Context.toast(
    @StringRes resource: Int,
    duration: Int,
    vararg args: Any
) {
    toast(this.getString(resource, *args), duration)
}

private fun Context.toast(message: String, duration: Int) {
    if (message.isEmpty()) {
        Timber.e("You must provide a String or Resource ID!")
        return
    }
    val toast = Toast.makeText(this, message, duration)
    toast.show()
}