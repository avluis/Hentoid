package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.bundle

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.activities.ToolsActivity]
 * through a Bundle
 */
class ToolsBundle(val bundle: Bundle = Bundle()) {

    var contentSearchBundle by bundle.bundle()
}