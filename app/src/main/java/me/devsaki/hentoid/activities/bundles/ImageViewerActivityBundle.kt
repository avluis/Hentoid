package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.*

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.activities.ImageViewerActivity]
 * through a Bundle
 */
class ImageViewerActivityBundle(val bundle: Bundle = Bundle()) {

    var contentId by bundle.long(default = 0)

    var searchParams by bundle.bundle()

    var imageIndex by bundle.int(default = -1)

    var pageNumber by bundle.int(default = -1)

    var scale by bundle.float(default = -1f)

    var isForceShowGallery by bundle.boolean(default = false)
}