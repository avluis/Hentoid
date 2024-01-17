package me.devsaki.hentoid.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import timber.log.Timber

object ToastHelper {
    fun toast(message: String) {
        toast(getInstance(), message, Toast.LENGTH_SHORT)
    }

    fun toast(@StringRes resource: Int) {
        toastShort(getInstance(), resource)
    }

    fun toast(@StringRes resource: Int, vararg args: Any) {
        toastShort(getInstance(), resource, *args)
    }

    fun toastShort(context: Context, @StringRes resource: Int, vararg args: Any) {
        toast(context, resource, Toast.LENGTH_SHORT, *args)
    }

    fun toastLong(context: Context, @StringRes resource: Int, vararg args: Any) {
        toast(context, resource, Toast.LENGTH_LONG, *args)
    }

    fun toast(
        context: Context,
        @StringRes resource: Int,
        duration: Int,
        vararg args: Any
    ) {
        toast(context, context.getString(resource, *args), duration)
    }

    private fun toast(context: Context, message: String, duration: Int) {
        if (message.isEmpty()) {
            Timber.e("You must provide a String or Resource ID!")
            return
        }
        val toast = Toast.makeText(context, message, duration)
        toast.show()
    }
}