package me.devsaki.hentoid.customssiv.decoder

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.text.TextUtils

@Throws(PackageManager.NameNotFoundException::class)
internal fun getResourceId(context: Context, uri: Uri): Int {
    val res: Resources
    val packageName = uri.authority
    if (context.packageName == packageName) {
        res = context.resources
    } else {
        val pm = context.packageManager
        res = pm.getResourcesForApplication(packageName!!)
    }

    var result = 0
    val segments = uri.pathSegments
    val size = segments.size
    if (size == 2 && segments[0] == "drawable") {
        val resName = segments[1]
        result = res.getIdentifier(resName, "drawable", packageName)
    } else if (size == 1 && TextUtils.isDigitsOnly(segments[0])) {
        try {
            result = segments[0].toInt()
        } catch (ignored: NumberFormatException) {
            // Ignored exception
        }
    }

    return result
}