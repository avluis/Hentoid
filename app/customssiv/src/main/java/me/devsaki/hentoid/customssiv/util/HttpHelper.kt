package me.devsaki.hentoid.customssiv.util

import java.net.URLDecoder
import java.util.Locale

/**
 * Return the extension of the file located at the given URI, in lowercase, without the leading '.'
 *
 * @param uri Location of the file
 * @return Extension of the file located at the given URI, in lowercase, without the leading '.'
 */
internal fun getExtensionFromUri(uri: String): String {
    val parts = UriParts(uri, true)
    return parts.extension
}

/**
 * Class to parse and manipulate Uri parts
 * Example source Uri : http://subdomain.host.ext:80/this/is/the/police.jpg?query=here#anchor
 *
 * @param lowercase True to convert the entire Uri to lowercase; false to keep as is
 */
internal class UriParts(uri: String, lowercase: Boolean = false) {
    val host: String // Host alone, subdomain included (e.g. http://subdomain.host.ext:80)
    var path: String // Entire path, host included and file not included (e.g. http://subdomain.host.ext:80/this/is/the)
    var fileNameNoExt: String // Filename without extension (e.g. police)
    var extension: String // File extension alone (e.g. jpg)
    var query: String // Query alone (e.g. query=here)
    private var fragment: String // Fragment alone (e.g. anchor)

    init {
        var uriNoParams =
            if (uri.contains("%3A") || uri.contains("%2F")) URLDecoder.decode(uri, "UTF-8") else uri
        uriNoParams = if (lowercase) uriNoParams.lowercase(Locale.getDefault()) else uriNoParams
        val fragmentIndex = uriNoParams.lastIndexOf('#')
        if (fragmentIndex > -1) {
            fragment = uriNoParams.substring(fragmentIndex + 1)
            uriNoParams = uriNoParams.substring(0, fragmentIndex)
        } else fragment = ""
        val paramsIndex = uriNoParams.lastIndexOf('?')
        if (paramsIndex > -1) {
            query = uriNoParams.substring(paramsIndex + 1)
            uriNoParams = uriNoParams.substring(0, paramsIndex)
        } else query = ""
        val pathIndex = uriNoParams.lastIndexOf('/')
        path = if (pathIndex > -1) uriNoParams.substring(0, pathIndex) else uriNoParams
        val protocolEndIndex = path.indexOf("://")
        val hostEndIndex = path.indexOf("/", protocolEndIndex + 3)
        host = if (hostEndIndex > -1) path.substring(0, hostEndIndex) else path

        val extIndex = uriNoParams.lastIndexOf('.')
        // No file extension detected
        if (extIndex < 0 || extIndex < pathIndex) {
            extension = ""
            fileNameNoExt = uriNoParams.substring(pathIndex + 1)
        } else {
            extension = uriNoParams.substring(extIndex + 1)
            fileNameNoExt = uriNoParams.substring(pathIndex + 1, extIndex)
        }
    }
}