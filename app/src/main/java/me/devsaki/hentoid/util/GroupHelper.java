package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Stream;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.IOException;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.util.file.FileHelper;
import timber.log.Timber;

/**
 * Utility class for Group-related operations
 */
public final class GroupHelper {

    private GroupHelper() {
        throw new IllegalStateException("Utility class");
    }


    public static Group getOrCreateNoArtistGroup(@NonNull final Context context, @NonNull final CollectionDAO dao) {
        String noArtistGroupName = context.getResources().getString(R.string.no_artist_group_name);
        Group result = dao.selectGroupByName(Grouping.ARTIST.getId(), noArtistGroupName);
        if (null == result) {
            result = new Group(Grouping.ARTIST, noArtistGroupName, -1);
            result.id = dao.insertGroup(result);
        }
        return result;
    }

    /**
     * Add the given Content to the given Group, and associate the latter with the given Attribute (if no prior association)
     *
     * @param group      Group to add the given Content to, and to associate with the given Attribute
     * @param attribute  Attribute the given Group should be associated with, if it has no prior association
     * @param newContent Content to put in the given Group
     * @param dao        DAO to be used
     */
    public static void addContentToAttributeGroup(@NonNull Group group, Attribute attribute, @NonNull Content newContent, @NonNull CollectionDAO dao) {
        addContentsToAttributeGroup(group, attribute, Stream.of(newContent).toList(), dao);
    }

    /**
     * Add the given Contents to the given Group, and associate the latter with the given Attribute (if no prior association)
     *
     * @param group       Group to add the given Content to, and to associate with the given Attribute
     * @param attribute   Attribute the given Group should be associated with, if it has no prior association
     * @param newContents List of Content to put in the given Group
     * @param dao         DAO to be used
     */
    public static void addContentsToAttributeGroup(@NonNull Group group, Attribute attribute, @NonNull List<Content> newContents, @NonNull CollectionDAO dao) {
        int nbContents;
        // Create group if it doesn't exist
        if (0 == group.id) {
            group.id = dao.insertGroup(group);
            if (attribute != null) attribute.putGroup(group);
            nbContents = 0;
        } else {
            nbContents = group.getItems().size();
        }
        for (Content content : newContents) {
            if (!isContentLinkedToGroup(content, group)) {
                GroupItem item = new GroupItem(content, group, nbContents++);
                dao.insertGroupItem(item);
            }
        }
    }

    /**
     * Update the JSON file that stores the groups with all the groups of the app
     *
     * @param context Context to be used
     * @param dao     DAO to be used
     * @return True if the groups JSON file has been updated properly; false instead
     */
    public static boolean updateGroupsJson(@NonNull Context context, @NonNull CollectionDAO dao) {
        Helper.assertNonUiThread();
        List<Group> customGroups = dao.selectGroups(Grouping.CUSTOM.getId());
        // Save custom groups (to be able to restore them in case the app gets uninstalled)
        JsonContentCollection contentCollection = new JsonContentCollection();
        contentCollection.setCustomGroups(customGroups);

        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(context, Preferences.getStorageUri());
        if (null == rootFolder) return false;

        try {
            JsonHelper.jsonToFile(context, contentCollection, JsonContentCollection.class, rootFolder, Consts.GROUPS_JSON_FILE_NAME);
        } catch (IOException | IllegalArgumentException e) {
            // NB : IllegalArgumentException might happen for an unknown reason on certain devices
            // even though all the file existence checks are in place
            // ("Failed to determine if primary:.Hentoid/groups.json is child of primary:.Hentoid: java.io.FileNotFoundException: Missing file for primary:.Hentoid/groups.json at /storage/emulated/0/.Hentoid/groups.json")
            Timber.e(e);
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.recordException(e);
            return false;
        }
        return true;
    }

    /**
     * Move the given Content to the given custom Group
     * NB : A Content can only be affected to one single custom group; moving it to multiple groups will only remember the last one
     *
     * @param content Content to move
     * @param group   Custom group to move the content to
     * @param dao     DAO to use
     * @return Updated Content
     */
    public static Content moveContentToCustomGroup(@NonNull final Content content, @Nullable final Group group, @NonNull final CollectionDAO dao) {
        Helper.assertNonUiThread();
        // Get all groupItems of the given content for custom grouping
        List<GroupItem> groupItems = dao.selectGroupItems(content.getId(), Grouping.CUSTOM);

        if (!groupItems.isEmpty()) {
            // Update the cover of the old groups if they used a picture from the book that is being moved
            for (GroupItem gi : groupItems) {
                Group g = gi.group.getTarget();
                if (g != null && !g.coverContent.isNull()) {
                    if (g.coverContent.getTargetId() == content.getId()) {
                        updateGroupCover(g, content.getId(), dao);
                    }
                }
            }

            // Delete them all
            dao.deleteGroupItems(Stream.of(groupItems).map(gi -> gi.id).toList());
        }

        // Create the new links from the given content to the target group
        if (group != null) {
            GroupItem newGroupItem = new GroupItem(content, group, -1);
            // Use this syntax because content will be persisted on JSON right after that
            content.groupItems.add(newGroupItem);
            // Commit new link to the DB
            content.groupItems.applyChangesToDb();

            // Add a picture to the target group if it didn't have one
            if (group.coverContent.isNull())
                group.coverContent.setAndPutTarget(content);
        }

        return content;
    }

    /**
     * Update the given Group's cover according to the removed Content ID
     * NB : This method does _not_ remove any Content from the given Group
     *
     * @param group             Group to update the cover from
     * @param contentIdToRemove Content ID removed from the given Group
     * @param dao               DAO to use
     */
    private static void updateGroupCover(@NonNull final Group group, long contentIdToRemove, @NonNull CollectionDAO dao) {
        List<Content> groupsContents = dao.selectContent(Helper.getPrimitiveArrayFromList(group.getContentIds()));

        // Empty group cover if there's just one content inside
        if (1 == groupsContents.size() && groupsContents.get(0).getId() == contentIdToRemove) {
            group.coverContent.setAndPutTarget(null);
            return;
        }

        // Choose 1st valid content cover
        for (Content c : groupsContents)
            if (c.getId() != contentIdToRemove) {
                ImageFile cover = c.getCover();
                if (cover.getId() > -1) {
                    group.coverContent.setAndPutTarget(c);
                    return;
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
    public static Group addArtistToAttributesGroup(@NonNull String name, @NonNull CollectionDAO dao) {
        Group artistGroup = new Group(Grouping.ARTIST, name, -1);
        artistGroup.id = dao.insertGroup(artistGroup);
        return artistGroup;
    }

    private static boolean isContentLinkedToGroup(@NonNull Content content, @NonNull Group group) {
        for (GroupItem item : content.getGroupItems(group.grouping)) {
            if (item.group.getTarget().equals(group)) return true;
        }
        return false;
    }
}
