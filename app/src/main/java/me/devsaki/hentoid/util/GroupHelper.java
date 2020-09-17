package me.devsaki.hentoid.util;

import com.annimon.stream.Stream;

import java.util.List;

import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.enums.Grouping;

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
            result.id =  dao.insertGroup(result);
        }
        return result;
    }

    // Does group exist ?
    // yes -> insert group item only to the bottom of the list
    // no -> create new group and insert group item inside
    public static void insertContent(CollectionDAO dao, Group group, Attribute attribute, Content newContent) {
        insertContent(dao, group, attribute, Stream.of(newContent).toList());
    }

    public static void insertContent(CollectionDAO dao, Group group, Attribute attribute, List<Content> newContents) {
        int nbContents;
        // Insert group if it doesn't exist
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
}
