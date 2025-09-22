package me.devsaki.hentoid.util

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.core.GROUPS_JSON_FILE_NAME
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.GroupItem
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.json.JsonContentCollection
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import timber.log.Timber

/**
 * Update the JSON file that stores the groups with all the groups of the app
 * NB : JSON is created to keep the information in case the app gets uninstalled
 * or the library gets refreshed
 *
 * @param context Context to be used
 * @param dao     DAO to be used
 * @return True if the groups JSON file has been updated properly; false instead
 */
suspend fun updateGroupsJson(context: Context, dao: CollectionDAO): Boolean =
    withContext(Dispatchers.IO) {
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
            getDocumentFromTreeUriString(context, Settings.getStorageUri(StorageLocation.PRIMARY_1))
                ?: return@withContext false

        try {
            jsonToFile(
                context, contentCollection,
                JsonContentCollection::class.java, rootFolder, GROUPS_JSON_FILE_NAME
            )
        } catch (e: Exception) {
            // NB : IllegalArgumentException might happen for an unknown reason on certain devices
            // even though all the file existence checks are in place
            // ("Failed to determine if primary:.Hentoid/groups.json is child of primary:.Hentoid: java.io.FileNotFoundException: Missing file for primary:.Hentoid/groups.json at /storage/emulated/0/.Hentoid/groups.json")
            Timber.e(e)
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.recordException(e)
            return@withContext false
        }
        return@withContext true
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
fun moveContentToCustomGroup(
    content: Content,
    group: Group?,
    dao: CollectionDAO,
    order: Int = -1,
): Content {
    assertNonUiThread()
    // Get all groupItems of the given content for custom grouping
    val groupItems = dao.selectGroupItems(content.id, Grouping.CUSTOM)

    if (groupItems.isNotEmpty()) {
        // Update the cover of the old groups if they used a picture from the book that is being moved
        for (gi in groupItems) {
            val g = gi.linkedGroup
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
 * Split Group unique str into its three components
 * First : Grouping name
 * Second : Group name
 * Third : Subtype
 */
fun splitUniqueStr(uniqueStr: String): Triple<String, String, Int> {
    val array = uniqueStr.split(SEPARATOR_CHAR)
    return if (array.size < 3) Triple("", "", 0)
    else Triple(array[0], array[1], array[2].toInt())
}