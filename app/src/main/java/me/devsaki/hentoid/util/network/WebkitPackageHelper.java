package me.devsaki.hentoid.util.network;

import android.util.AndroidRuntimeException;
import android.webkit.CookieManager;

public class WebkitPackageHelper {
    private static boolean isWebViewAvailable;
    private static boolean isWebViewUpdating = false;

    public static void setWebViewAvailable() {
        try {
            CookieManager.getInstance();
            isWebViewAvailable = true;
        } catch (AndroidRuntimeException e) {
            String message = e.getMessage();
            if (message!=null && message.contains("WebView")) {
                isWebViewAvailable = false;
            } else throw e;
        }
    }

    public static boolean getWebViewAvailable() {
        return isWebViewAvailable;
    }

    public static void setWebViewUpdating(boolean isWebViewUpdating) {
        WebkitPackageHelper.isWebViewUpdating = isWebViewUpdating;
    }

    public static boolean getWebViewUpdating() {
        return isWebViewUpdating;
    }
}
