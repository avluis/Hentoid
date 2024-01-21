package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.bundle
import me.devsaki.hentoid.util.long
import me.devsaki.hentoid.util.longArray

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.activities.LibraryActivity]
 * through a Bundle
 */
class LibraryActivityBundle(val bundle: Bundle = Bundle()) {

    var contentSearchArgs by bundle.bundle()

    var groupSearchArgs by bundle.bundle()
}