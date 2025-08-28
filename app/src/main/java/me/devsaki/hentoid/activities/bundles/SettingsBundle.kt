package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.activities.settings.SettingsActivity]
 * through a Bundle
 */
class SettingsBundle(val bundle: Bundle = Bundle()) {

    var isViewerSettings by bundle.boolean(default = false)

    var isBrowserSettings by bundle.boolean(default = false)

    var isDownloaderSettings by bundle.boolean(default = false)

    var isStorageSettings by bundle.boolean(default = false)

    var site by bundle.int(default = Site.NONE.code)
}