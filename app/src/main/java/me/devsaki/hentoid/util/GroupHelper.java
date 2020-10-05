package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Stream;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.IOException;
import java.util.List;

import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.json.JsonContentCollection;
import timber.log.Timber;

/**
 * Utility class for Content-related operations
 */
public final class GroupHelper {

    private GroupHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static final int FLAG_UNCATEGORIZED = 99;


    public static List<Grouping> getGroupingsToProcess() {
        return Stream.of(Grouping.values()).filter(Grouping::canReorderBooks).toList();
    }

    public static Group getOrCreateUncategorizedGroup(CollectionDAO dao) {
        Group result = dao.selectGroupByFlag(Grouping.CUSTOM.getId(), FLAG_UNCATEGORIZED);
        if (null == result) {
            result = new Group(Grouping.CUSTOM, "Uncategorized", 0);
            result.flag = GroupHelper.FLAG_UNCATEGORIZED;
            result.id = dao.insertGroup(result);
        }
        return result;
    }

    public static void insertContent(CollectionDAO dao, Group group, Attribute attribute, Content newContent) {
        insertContent(dao, group, attribute, Stream.of(newContent).toList());
    }

    public static void insertContent(CollectionDAO dao, Group group, Attribute attribute, List<Content> newContents) {
        int nbContents;
        // Create group if it doesn't exist
        if (0 == group.id) {
            dao.insertGroup(group);
            if (attribute != null) attribute.group.setAndPutTarget(group);
            nbContents = 0;
        } else {
            nbContents = group.getItems().size();
        }
        for (Content book : newContents) {
            GroupItem item = new GroupItem(book, group, nbContents++);
            dao.insertGroupItem(item);
        }
    }

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
}
