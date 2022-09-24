package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.string

/**
 * Helper class to transfer payload data to [me.devsaki.hentoid.viewholders.RuleItem]
 * through a Bundle
 */
class RenamingRuleBundle(val bundle: Bundle = Bundle()) {
    var attrType by bundle.int()

    var source by bundle.string()

    var target by bundle.string()

    var selected by bundle.boolean(false)

    val isEmpty get() = bundle.isEmpty
}