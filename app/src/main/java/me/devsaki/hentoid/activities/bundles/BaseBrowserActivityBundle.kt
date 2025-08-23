package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.string

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.activities.BaseBrowserActivity}
 * through a Bundle
 */
class BaseBrowserActivityBundle(val bundle: Bundle = Bundle()) {

    var url: String by bundle.string(default = "")
}