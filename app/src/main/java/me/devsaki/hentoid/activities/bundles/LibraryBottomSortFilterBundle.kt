package me.devsaki.hentoid.activities.bundles

import android.os.Bundle
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.fragments.library.LibraryBottomSortFilterFragment]
 * through a Bundle
 */
class LibraryBottomSortFilterBundle(val bundle: Bundle = Bundle()) {

    var isGroupsDisplayed by bundle.boolean(default = false)

    var isUngroupedGroupDisplayed by bundle.boolean(default = false)

    var showTabIndex by bundle.int(default = 0)
}