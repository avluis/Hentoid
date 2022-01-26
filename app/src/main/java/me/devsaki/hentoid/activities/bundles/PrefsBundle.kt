package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.activities.PrefsActivity}
 * through a Bundle
 */
class PrefsBundle(val bundle: Bundle) {

    constructor() : this(Bundle())

    var isViewerPrefs by bundle.boolean(default = false)

    var isBrowserPrefs by bundle.boolean(default = false)

    var isDownloaderPrefs by bundle.boolean(default = false)

    var isStoragePrefs by bundle.boolean(default = false)

    fun toBundle() = bundle
}