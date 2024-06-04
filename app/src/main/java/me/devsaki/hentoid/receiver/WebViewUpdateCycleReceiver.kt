package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.devsaki.hentoid.util.network.WebkitPackageHelper.getWebViewAvailable
import me.devsaki.hentoid.util.network.WebkitPackageHelper.getWebViewUpdating
import me.devsaki.hentoid.util.network.WebkitPackageHelper.setWebViewAvailable
import me.devsaki.hentoid.util.network.WebkitPackageHelper.setWebViewUpdating
import timber.log.Timber

class WebViewUpdateCycleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.data == null) {
            return
        }

        val packageName = intent.data!!.schemeSpecificPart
        /* Android L-M (AOSP) com.android.webview           (GMS) com.google.android.webview
           Android N-P (AOSP) com.android.webview           (GMS) com.android.chrome
                                                    (Custom ROMs) com.google.android.apps.chrome
           Android Q   (AOSP) com.android.webview           (GMS) com.google.android.webview
                                                    (Custom ROMs) com.google.android.webview.debug
                                                                  com.android.webview

           (sources: https://chromium.googlesource.com/chromium/src.git/+/refs/heads/main/android_webview/docs/webview-providers.md
                     https://chromium.googlesource.com/chromium/src.git/+/refs/heads/main/android_webview/docs/aosp-system-integration.md)

           Since Android Q, (mostly) devices with GMS have the "real" Chrome/WebView library in a
           separate hidden "Trichrome" package. Devices with "Trichrome" should also have a system-
           protected "Fallback WebView" package (exactly for the purpose of updates that can't
           cause app crashes at all), but the checks are inexpensive anyway, so why not.
           "app.vanadium.webview" and "app.vanadium.trichromelibrary" relate to GrapheneOS, and
           "org.bromite.webview" relates to Bromite.

           Also: anyone using a beta/canary/dev/debug build or any other WebView implementation is
           really asking for trouble. Only add OEM-specific package names here or very popular
           production-ready WebView implementations.
         */
        if (packageName == "com.android.webview" || packageName == "com.android.chrome" || packageName == "com.google.android.webview" || packageName == "com.google.android.apps.chrome" || packageName == "com.google.android.webview.debug" || packageName == "org.bromite.webview" || packageName == "app.vanadium.webview" || packageName == "app.vanadium.trichromelibrary" || packageName == "com.google.android.trichromelibrary") {
            setWebViewAvailable()
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                if (!getWebViewAvailable()) { // If some other WebView provider is available, then we shouldn't care
                    Timber.w(
                        "The last WebView provider (package %s) has been removed, hoping it is an update",
                        packageName
                    )
                    setWebViewUpdating(true)
                } else Timber.i(
                    "A WebView provider has been removed (package %s), but another implementation is available",
                    packageName
                )
            } else if (intent.action == Intent.ACTION_PACKAGE_ADDED || intent.action == Intent.ACTION_PACKAGE_REPLACED) {
                if (getWebViewAvailable()) { // ...but does the system recognize it as a WebView provider?
                    Timber.i(
                        "Got WebView back! Implementation now provided by package %s",
                        packageName
                    )
                    setWebViewUpdating(false)
                } else {
                    if (getWebViewUpdating()) Timber.w(
                        "WebView provider candidate (package %s) has installed, but there is still no implementation available",
                        packageName
                    )
                }
            }
        }
    }
}