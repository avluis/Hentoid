package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.long
import me.devsaki.hentoid.util.longArray

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.activities.MetadataEditActivity]
 * through a Bundle
 */
class MetaEditActivityBundle(val bundle: Bundle = Bundle()) {

    var contentIds by bundle.longArray()

    var excludeMode by bundle.boolean(default = false)

    var idToReplace by bundle.long(default = -1)
}