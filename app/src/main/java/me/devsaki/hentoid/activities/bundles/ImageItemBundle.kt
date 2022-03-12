package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int

/**
 * Helper class to transfer payload data to [me.devsaki.hentoid.viewholders.ImageFileItem]
 * through a Bundle
 */
class ImageItemBundle(val bundle: Bundle = Bundle()) {

    var isFavourite by bundle.boolean()

    var chapterOrder by bundle.int()

    val isEmpty get() = bundle.isEmpty
}