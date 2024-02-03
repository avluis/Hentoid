package me.devsaki.hentoid.util.network

import android.util.AndroidRuntimeException
import android.webkit.CookieManager

object WebkitPackageHelper {
    private var isWebViewAvailable = false
    private var isWebViewUpdating = false

    fun setWebViewAvailable() {
        isWebViewAvailable = try {
            CookieManager.getInstance()
            true
        } catch (e: AndroidRuntimeException) {
            val message = e.message
            if (message != null && message.contains("WebView")) {
                false
            } else throw e
        }
    }

    fun getWebViewAvailable(): Boolean {
        return isWebViewAvailable
    }

    fun setWebViewUpdating(isWebViewUpdating: Boolean) {
        this.isWebViewUpdating = isWebViewUpdating
    }

    fun getWebViewUpdating(): Boolean {
        return isWebViewUpdating
    }
}