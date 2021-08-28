package me.devsaki.hentoid.core

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.ToastHelper
import timber.log.Timber

/**
 * Open the given url using the device's app(s) of choice
 *
 * @param url Url to be opened
 */
fun Context.startBrowserActivity(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.e(e, "No activity found to open $url")
        ToastHelper.toast(this, R.string.error_browser, Toast.LENGTH_LONG)
    }
}

inline fun <reified T : Activity> Context.startLocalActivity() {
    startActivity(Intent(this, T::class.java))
}