package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.long
import me.devsaki.hentoid.util.string

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.activities.QueueActivityK]
 * through a Bundle
 */
class QueueActivityBundle(val bundle: Bundle = Bundle()) {

    var isErrorsTab by bundle.boolean(default = false)

    var contentHash by bundle.long(default = 0)

    var reviveDownloadForSiteCode by bundle.int(default = Site.NONE.code)

    var reviveOldCookie by bundle.string(default = "")
}