package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.string

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.activities.PrefsActivity}
 * through a Bundle
 */
class BaseWebActivityBundle(private val bundle: Bundle) {

    constructor() : this(Bundle())

    var url by bundle.string(default = "")

    fun toBundle() = bundle
}