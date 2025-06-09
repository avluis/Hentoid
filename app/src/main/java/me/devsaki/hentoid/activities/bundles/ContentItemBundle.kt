package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.long
import me.devsaki.hentoid.util.string

/**
 * Helper class to transfer payload data to [me.devsaki.hentoid.viewholders.ContentItem]
 * through a Bundle
 */
class ContentItemBundle(val bundle: Bundle = Bundle()) {

    var isBeingDeleted by bundle.boolean()

    var isFavourite by bundle.boolean()

    var rating by bundle.int()

    var isCompleted by bundle.boolean()

    var reads by bundle.long()

    var readPagesCount by bundle.int()

    var coverUri by bundle.string()

    var title by bundle.string()

    var downloadMode by bundle.int()

    var frozen by bundle.boolean()

    var processed by bundle.boolean()

    var qtyPages by bundle.int()

    var size by bundle.long()

    val isEmpty get() = bundle.isEmpty
}