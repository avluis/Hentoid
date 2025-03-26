package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.int

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.activities.prefs.PreferencesSourceSpecificsActivity]
 * through a Bundle
 */
class PrefsSourceSpecificsBundle(val bundle: Bundle = Bundle()) {

    var site by bundle.int(default = Site.NONE.code)
}