package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean

/**
 * Helper class to transfer payload data to [me.devsaki.hentoid.viewholders.DuplicateItem]
 * through a Bundle
 */
class DuplicateItemBundle(val bundle: Bundle = Bundle()) {

    var isKeep by bundle.boolean()

    var isBeingDeleted by bundle.boolean()

    val isEmpty get() = bundle.isEmpty
}