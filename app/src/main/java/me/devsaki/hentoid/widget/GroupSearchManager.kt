package me.devsaki.hentoid.widget

import android.os.Bundle
import androidx.lifecycle.LiveData
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.string

class GroupSearchManager(val dao: CollectionDAO) {

    private val values = GroupSearchBundle()


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

    fun setFilterFavourites(value: Boolean) {
        values.filterFavourites = value
    }

    fun setFilterNonFavourites(value: Boolean) {
        values.filterNonFavourites = value
    }

    fun setFilterRating(value: Int) {
        values.filterRating = value
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

    fun clearFilters() {
        setQuery("")
        setArtistGroupVisibility(Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS)
        setFilterFavourites(false)
        setFilterNonFavourites(false)
        setFilterRating(-1)
    }

    fun getGroups(): LiveData<List<Group>> {
        return dao.selectGroupsLive(
            values.groupingId,
            values.query,
            values.sortField,
            values.sortDesc,
            values.artistGroupVisibility,
            values.filterFavourites,
            values.filterNonFavourites,
            values.filterRating
        )
    }

    fun getAllGroups(): LiveData<List<Group>> {
        return dao.selectGroupsLive(
            values.groupingId,
            "",
            values.sortField,
            values.sortDesc,
            Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS,
            false,
            false,
            -1
        )
    }

    class GroupSearchBundle(val bundle: Bundle = Bundle()) {

        var filterFavourites by bundle.boolean(default = false)

        var filterNonFavourites by bundle.boolean(default = false)

        var filterRating by bundle.int(default = -1)

        var artistGroupVisibility by bundle.int(default = Preferences.getArtistGroupVisibility())

        var query by bundle.string(default = "")

        var groupingId by bundle.int(default = Preferences.getGroupingDisplay().id)

        var sortField by bundle.int(default = Settings.groupSortField)

        var sortDesc by bundle.boolean(default = Settings.isGroupSortDesc)

        fun isFilterActive(): Boolean {
            return query.isNotEmpty()
                    || artistGroupVisibility != Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS
                    || filterFavourites
                    || filterNonFavourites
                    || filterRating > -1
        }
    }
}