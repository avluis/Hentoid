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
     * @param dao        DAO to be used
     * @param group      Group to add the given Content to, and to associate with the given Attribute
     * @param attribute  Attribute the given Group should be associated with, if it has no prior association
     * @param newContent Content to put in the given Group
     */
    public static void addContentToAttributeGroup(CollectionDAO dao, Group group, Attribute attribute, Content newContent) {
        addContentsToAttributeGroup(dao, group, attribute, Stream.of(newContent).toList());
    }

    /**
     * Add the given Contents to the given Group, and associate the latter with the given Attribute (if no prior association)
     *
     * @param dao         DAO to be used
     * @param group       Group to add the given Content to, and to associate with the given Attribute
     * @param attribute   Attribute the given Group should be associated with, if it has no prior association
     * @param newContents List of Content to put in the given Group
     */
    public static void addContentsToAttributeGroup(CollectionDAO dao, Group group, Attribute attribute, List<Content> newContents) {
        int nbContents;
        // Create group if it doesn't exist
        if (0 == group.id) {
            group.id = dao.insertGroup(group);
            if (attribute != null) attribute.putGroup(group);
            nbContents = 0;
        } else {
            nbContents = group.getItems().size();
        }
        for (Content book : newContents) {
            GroupItem item = new GroupItem(book, group, nbContents++);
            dao.insertGroupItem(item);
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

    // TODO doc
    public static Content moveContentToCustomGroup(@NonNull final Content content, @Nullable final Group group, @NonNull final CollectionDAO dao) {
        Helper.assertNonUiThread();
        // Get all groupItems of the given content for custom grouping
        List<GroupItem> groupItems = dao.selectGroupItems(content.getId(), Grouping.CUSTOM);

        if (!groupItems.isEmpty()) {
            // Update the cover of the old groups if they used a picture from the book that is being moved
            for (GroupItem gi : groupItems) {
                Group g = gi.group.getTarget();
                if (g != null && !g.picture.isNull()) {
                    ImageFile groupCover = g.picture.getTarget();
                    if (groupCover.getContent().getTargetId() == content.getId()) {
                        updateGroupCover(g, content.getId());
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
            if (group.picture.isNull())
                group.picture.setAndPutTarget(content.getCover());
        }

        return content;
    }

    // TODO doc
    private static void updateGroupCover(@NonNull final Group g, long contentIdToRemove) {
        List<Content> groupsContents = g.getContents();

        // Empty group cover if there's just one content inside
        if (1 == groupsContents.size() && groupsContents.get(0).getId() == contentIdToRemove) {
            g.picture.setAndPutTarget(null);
            return;
        }

        // Choose 1st valid content cover
        for (Content c : groupsContents)
            if (c.getId() != contentIdToRemove) {
                ImageFile cover = c.getCover();
                if (cover.getId() > -1) {
                    g.picture.setAndPutTarget(cover);
                    return;
                }
            }
    }
}
