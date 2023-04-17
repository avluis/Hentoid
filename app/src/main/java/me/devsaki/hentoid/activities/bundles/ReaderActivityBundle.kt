package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.bundle
import me.devsaki.hentoid.util.float
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.long

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.activities.ReaderActivity]
 * through a Bundle
 */
class ReaderActivityBundle(val bundle: Bundle = Bundle()) {

    var contentId by bundle.long(default = 0)

    var searchParams by bundle.bundle()

    var imageIndex by bundle.int(default = -1)

    var pageNumber by bundle.int(default = -1)

    var scale by bundle.float(default = -1f)

    var isForceShowGallery by bundle.boolean(default = false)

    var isOpenFavPages by bundle.boolean(default = false)
}