package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.long
import me.devsaki.hentoid.util.string

/**
 * Helper class to transfer payload data to [me.devsaki.hentoid.viewholders.FileItem]
 * through a Bundle
 */
class FileItemBundle(val bundle: Bundle = Bundle()) {

    var coverUri by bundle.string()
    var contentId by bundle.long()
    var processed by bundle.boolean()
    var type by bundle.int()
    var subType by bundle.int()

    val isEmpty get() = bundle.isEmpty
}