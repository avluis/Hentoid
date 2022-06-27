package me.devsaki.hentoid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import me.devsaki.hentoid.util.network.WebkitPackageHelper;
import timber.log.Timber;

public class WebViewUpdateCycleReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }

        final String packageName = intent.getData().getSchemeSpecificPart();
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
        if (packageName.equals("com.android.webview") || packageName.equals("com.android.chrome") || packageName.equals("com.google.android.webview")
                || packageName.equals("com.google.android.apps.chrome") || packageName.equals("com.google.android.webview.debug") || packageName.equals("org.bromite.webview")
                || packageName.equals("app.vanadium.webview") || packageName.equals("app.vanadium.trichromelibrary") || packageName.equals("com.google.android.trichromelibrary")) {
            WebkitPackageHelper.setWebViewAvailable();
            if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
                if (!WebkitPackageHelper.getWebViewAvailable()) { // If some other WebView provider is available, then we shouldn't care
                    Timber.w("The last WebView provider (package %s) has been removed, hoping it is an update", packageName);
                    WebkitPackageHelper.setWebViewUpdating(true);
                } else
                    Timber.i("A WebView provider has been removed (package %s), but another implementation is available", packageName);

            } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED) || intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED)) {
                if (WebkitPackageHelper.getWebViewAvailable()) { // ...but does the system recognize it as a WebView provider?
                    Timber.i("Got WebView back! Implementation now provided by package %s", packageName);
                    WebkitPackageHelper.setWebViewUpdating(false);
                } else {
                    if (WebkitPackageHelper.getWebViewUpdating())
                        Timber.w("WebView provider candidate (package %s) has installed, but there is still no implementation available", packageName);
                }
            }
        }
    }
}
