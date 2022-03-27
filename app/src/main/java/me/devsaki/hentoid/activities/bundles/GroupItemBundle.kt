package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.string

/**
 * Helper class to transfer payload data to [me.devsaki.hentoid.viewholders.GroupDisplayItem]
 * through a Bundle
 */
class GroupItemBundle(val bundle: Bundle = Bundle()) {

    var coverUri by bundle.string()

    var isFavourite by bundle.boolean()

    val isEmpty get() = bundle.isEmpty
}