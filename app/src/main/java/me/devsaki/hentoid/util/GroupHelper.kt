package me.devsaki.hentoid.util

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.GROUPS_JSON_FILE_NAME
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.GroupItem
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.json.JsonContentCollection
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import timber.log.Timber
import java.io.IOException

fun getOrCreateNoArtistGroup(context: Context, dao: CollectionDAO): Group {
    val noArtistGroupName = context.resources.getString(R.string.no_artist_group_name)
    var result = dao.selectGroupByName(Grouping.ARTIST.id, noArtistGroupName)
    if (null == result) {
        result = Group(Grouping.ARTIST, noArtistGroupName, -1)
        result.id = dao.insertGroup(result)
    }
    return result
}

/**
 * Add the given Content to the given Group, and associate the latter with the given Attribute (if no prior association)
 *
 * @param group      Group to add the given Content to, and to associate with the given Attribute
 * @param attribute  Attribute the given Group should be associated with, if it has no prior association
 * @param newContent Content to put in the given Group
 * @param dao        DAO to be used
 */
fun addContentToAttributeGroup(
    group: Group,
    attribute: Attribute?,
    newContent: Content,
    dao: CollectionDAO
) {
    addContentsToAttributeGroup(group, attribute, listOf(newContent), dao)
}

/**
 * Add the given Contents to the given Group, and associate the latter with the given Attribute (if no prior association)
 *
 * @param group       Group to add the given Content to, and to associate with the given Attribute
 * @param attribute   Attribute the given Group should be associated with, if it has no prior association
 * @param newContents List of Content to put in the given Group
 * @param dao         DAO to be used
 */
fun addContentsToAttributeGroup(
    group: Group,
    attribute: Attribute?,
    newContents: List<Content>,
    dao: CollectionDAO
) {
    var nbContents: Int
    // Create group if it doesn't exist
    if (0L == group.id) {
        group.id = dao.insertGroup(group)
        attribute?.putGroup(group)
        nbContents = 0
    } else {
        nbContents = group.getItems().size
    }
    for (content in newContents) {
        if (!isContentLinkedToGroup(content, group)) {
            val item = GroupItem(content, group, nbContents++)
            dao.insertGroupItem(item)
        }
    }
}

/**
 * Update the JSON file that stores the groups with all the groups of the app
 * NB : JSON is created to keep the information in case the app gets uninstalled
 * or the library gets refreshed
 *
 * @param context Context to be used
 * @param dao     DAO to be used
 * @return True if the groups JSON file has been updated properly; false instead
 */
fun updateGroupsJson(context: Context, dao: CollectionDAO): Boolean {
    assertNonUiThread()

    val contentCollection = JsonContentCollection()

    // Save dynamic groups
    val dynamicGroups = dao.selectGroups(Grouping.DYNAMIC.id)
    contentCollection.replaceGroups(Grouping.DYNAMIC, dynamicGroups)

    // Save custom groups
    val customGroups = dao.selectGroups(Grouping.CUSTOM.id)
    contentCollection.replaceGroups(Grouping.CUSTOM, customGroups)

    // Save other groups whose favourite or rating has been set
    val editedArtistGroups = dao.selectEditedGroups(Grouping.ARTIST.id)
    contentCollection.replaceGroups(Grouping.ARTIST, editedArtistGroups)
    val editedDateGroups = dao.selectEditedGroups(Grouping.DL_DATE.id)
    contentCollection.replaceGroups(Grouping.DL_DATE, editedDateGroups)

    val rootFolder =
        getDocumentFromTreeUriString(context, Preferences.getStorageUri(StorageLocation.PRIMARY_1))
            ?: return false

    try {
        jsonToFile(
            context, contentCollection,
            JsonContentCollection::class.java, rootFolder, GROUPS_JSON_FILE_NAME
        )
    } catch (e: IOException) {
        // NB : IllegalArgumentException might happen for an unknown reason on certain devices
        // even though all the file existence checks are in place
        // ("Failed to determine if primary:.Hentoid/groups.json is child of primary:.Hentoid: java.io.FileNotFoundException: Missing file for primary:.Hentoid/groups.json at /storage/emulated/0/.Hentoid/groups.json")
        Timber.e(e)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.recordException(e)
        return false
    } catch (e: IllegalArgumentException) {
        Timber.e(e)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.recordException(e)
        return false
    }
    return true
}

/**
 * Move the given Content to the given custom Group
 * NB : A Content can only be affected to one single custom group; moving it to multiple groups will only remember the last one
 *
 * @param content Content to move (must have an ID already)
 * @param group   Custom group to move the content to
 * @param dao     DAO to use
 * @return Updated Content
 */
fun moveContentToCustomGroup(content: Content, group: Group?, dao: CollectionDAO): Content {
    return moveContentToCustomGroup(content, group, -1, dao)
}

fun moveContentToCustomGroup(
    content: Content,
    group: Group?,
    order: Int,
    dao: CollectionDAO
): Content {
    assertNonUiThread()
    // Get all groupItems of the given content for custom grouping
    val groupItems = dao.selectGroupItems(content.id, Grouping.CUSTOM)

    if (groupItems.isNotEmpty()) {
        // Update the cover of the old groups if they used a picture from the book that is being moved
        for (gi in groupItems) {
            val g = gi.group.target
            if (g != null && !g.coverContent.isNull) {
                if (g.coverContent.targetId == content.id) {
                    updateGroupCover(g, content.id, dao)
                }
            }
        }

        // Delete them all
        dao.deleteGroupItems(groupItems.map { it.id })
    }

    // Create the new links from the given content to the target group
    if (group != null) {
        val newGroupItem = GroupItem(content, group, order)
        // Use this syntax because content will be persisted on JSON right after that
        content.groupItems.add(newGroupItem)
        // Commit new link to the DB
        content.groupItems.applyChangesToDb()

        // Add a picture to the target group if it didn't have one
        if (group.coverContent.isNull) group.coverContent.setAndPutTarget(content)
    }

    return content
}

/**
 * Update the given Group's cover according to the removed Content ID
 * NB : This method does _not_ remove any Content from the given Group
 *
 * @param group             Group to update the cover from
 * @param contentIdToRemove Content ID removed from the given Group
 * @param dao               DAO to use
 */
private fun updateGroupCover(group: Group, contentIdToRemove: Long, dao: CollectionDAO) {
    val contentIds = group.contentIds
    val groupsContents: List<Content> = dao.selectContent(contentIds.toLongArray())

    // Empty group cover if there's just one content inside
    if (1 == groupsContents.size && groupsContents[0].id == contentIdToRemove) {
        group.coverContent.setAndPutTarget(null)
        return
    }

    // Choose 1st valid content cover
    for (c in groupsContents) if (c.id != contentIdToRemove) {
        val cover = c.cover
        if (cover.id > -1) {
            group.coverContent.setAndPutTarget(c)
            return
        }
    }
}

/**
 * Create a new group with the given name inside the Artists grouping
 *
 * @param name Name of the group to create
 * @param dao  DAO to use
 * @return Resulting group
 */
fun addArtistToAttributesGroup(name: String, dao: CollectionDAO): Group {
    val artistGroup = Group(Grouping.ARTIST, name, -1)
    artistGroup.id = dao.insertGroup(artistGroup)
    return artistGroup
}

/**
 * Indicate wether the given Content is linked to the given Group
 *
 * @param content Content to test
 * @param group   Group to test against
 * @return True if the given Content is linked to the given Group; false if not
 */
private fun isContentLinkedToGroup(content: Content, group: Group): Boolean {
    for (item in content.getGroupItems(group.grouping)) {
        if (item.group.target == group) return true
    }
    return false
}

/**
 * Remove the given Content from the given Grouping
 *
 * @param grouping Grouping to remove the given Content from
 * @param content  Content to remove
 * @param dao      DAO to use
 */
fun removeContentFromGrouping(grouping: Grouping, content: Content, dao: CollectionDAO) {
    val toRemove = HashSet<GroupItem>()
    val needCoverUpdate: MutableList<Group> = ArrayList()
    for (gi in content.groupItems) {
        gi.getGroup()?.let { g ->
            if (g.grouping == grouping) {
                toRemove.add(gi)
                if (g.coverContent.targetId == content.id) needCoverUpdate.add(g)
            }
        }
    }

    // If applicable, remove Group cover
    if (needCoverUpdate.isNotEmpty())
        for (g in needCoverUpdate)
            updateGroupCover(g, content.id, dao)

    // Remove content from grouping
    content.groupItems.removeAll(toRemove)
    dao.insertContentCore(content)

    // Remove GroupItems from the DB
    dao.deleteGroupItems(toRemove.map { it.id })
}