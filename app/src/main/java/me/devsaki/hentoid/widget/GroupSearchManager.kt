package me.devsaki.hentoid.widget

import android.os.Bundle
import androidx.lifecycle.LiveData
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.string

class GroupSearchManager(val dao: CollectionDAO) {

    private val values = GroupSearchBundle()

    fun setFilterFavourites(value: Boolean) {
        values.filterFavourites = value
    }

    fun setQuery(value: String) {
        values.query = value
    }

    fun setGrouping(value: Grouping) {
        values.groupingId = value.id
    }

    fun setArtistGroupVisibility(value: Int) {
        values.artistGroupVisibility = value
    }

    fun setSortField(value: Int) {
        values.sortField = value
    }

    fun setSortDesc(value: Boolean) {
        values.sortDesc = value
    }

    fun toBundle(): Bundle {
        val result = Bundle()
        saveToBundle(result)
        return result
    }

    fun saveToBundle(b: Bundle) {
        b.putAll(values.bundle)
    }

    fun loadFromBundle(b: Bundle) {
        values.bundle.putAll(b)
    }

    fun getGroups(): LiveData<List<Group>> {
        return dao.selectGroupsLive(
            values.groupingId,
            values.query,
            values.sortField,
            values.sortDesc,
            values.artistGroupVisibility,
            values.filterFavourites
        )
    }

    class GroupSearchBundle(val bundle: Bundle = Bundle()) {

        var filterFavourites by bundle.boolean(default = false)

        var artistGroupVisibility by bundle.int(default = Preferences.getArtistGroupVisibility())

        var query by bundle.string(default = "")

        var groupingId by bundle.int(default = Preferences.getGroupingDisplay().id)

        var sortField by bundle.int(default = Preferences.getGroupSortField())

        var sortDesc by bundle.boolean(default = Preferences.isGroupSortDesc())
    }
}