package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.string

/**
 * Helper class to transfer payload data to [me.devsaki.hentoid.viewholders.FileItem]
 * through a Bundle
 */
class FileItemBundle(val bundle: Bundle = Bundle()) {

    var coverUri by bundle.string()

    val isEmpty get() = bundle.isEmpty
}