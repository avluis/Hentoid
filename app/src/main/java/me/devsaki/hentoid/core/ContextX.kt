package me.devsaki.hentoid.core

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.widget.Toast
import androidx.core.util.Consumer
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import me.devsaki.hentoid.views.NestedScrollWebView
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

fun Context.clearWebviewCache(callback: Consumer<Boolean>?) {
    // Clear webview cache (needs to execute inside the activity's Looper)
    val h = Handler(Looper.getMainLooper())
    h.post {
        var webView: WebView?
        if (WebkitPackageHelper.getWebViewAvailable()) {
            try {
                webView = NestedScrollWebView(this)
                callback?.accept(true)
            } catch (nfe: Resources.NotFoundException) {
                // Some older devices can crash when instantiating a WebView, due to a Resources$NotFoundException
                // Creating with the application Context fixes this, but is not generally recommended for view creation
                webView = NestedScrollWebView(Helper.getFixedContext(this))
                callback?.accept(true)
            }
            webView?.clearCache(true)
        } else {
            callback?.accept(false)
        }
    }
}

fun Context.clearAppCache() {
    try {
        val dir = this.cacheDir
        FileHelper.removeFile(dir)
    } catch (e: Exception) {
        Timber.e(e, "Error when clearing app cache upon update")
    }
}