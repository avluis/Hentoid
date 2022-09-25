package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.string

/**
 * Helper class to transfer payload data to [me.devsaki.hentoid.viewholders.AttributeItem]
 * through a Bundle
 */
class AttributeItemBundle(val bundle: Bundle = Bundle()) {
    var name by bundle.string()

    var count by bundle.int()

    var selected by bundle.boolean(false)

    val isEmpty get() = bundle.isEmpty
}