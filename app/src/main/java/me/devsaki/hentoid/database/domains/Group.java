package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import java.util.List;
import java.util.Objects;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.converter.PropertyConverter;
import io.objectbox.relation.ToMany;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.util.Helper;

@Entity
public class Group {

    @Id
    public long id;
    @Index
    @Convert(converter = GroupingConverter.class, dbType = Integer.class)
    public Grouping grouping;
    public String name;
    @Backlink(to = "group")
    public ToMany<GroupItem> items;
    // Targetting the content instead of the picture itself because
    // 1- That's the logic of the UI
    // 2- Pictures within a given Content are sometimes entirely replaced, breaking that link
    public ToOne<Content> coverContent;
    // in Grouping.ARTIST : 0 = Artist; 1 = Group
    // in Grouping.CUSTOM : 0 = Custom; 1 = Ungrouped
    public int subtype;
    public int order;
    public boolean hasCustomBookOrder = false;
    public int propertyMin;
    public int propertyMax;
    public String searchUri = "";
    private boolean favourite = false;
    private int rating = 0;

    // Needs to be in the DB to keep the information when deletion takes a long time
    // and user navigates away; no need to save that into JSON
    private boolean isBeingDeleted = false;
    // Useful only during cleanup operations; no need to get it into the JSON
    private boolean isFlaggedForDeletion = false;


    public Group() { // Required by ObjectBox when an alternate constructor exists
    }

    public Group(@NonNull final Grouping grouping, @NonNull final String name, int order) {
        this.grouping = grouping;
        this.name = name;
        this.order = order;
    }

    public long getId() {
        return this.id;
    }

    public List<Long> getContentIds() {
        return Stream.of(items).withoutNulls().sortBy(i -> i.order).map(GroupItem::getContentId).toList();
    }

    public List<GroupItem> getItems() {
        return items;
    }

    public Group setItems(List<GroupItem> items) {
        // We do want to compare array references, not content
        if (items != null && items != this.items) {
            this.items.clear();
            this.items.addAll(items);
        }
        return this;
    }

    public int getSubtype() {
        return subtype;
    }

    public Group setSubtype(int subtype) {
        this.subtype = subtype;
        return this;
    }

    public int getOrder() {
        return order;
    }

    public boolean isBeingDeleted() {
        return isBeingDeleted;
    }

    public void setIsBeingDeleted(boolean isBeingDeleted) {
        this.isBeingDeleted = isBeingDeleted;
    }

    public boolean isFlaggedForDeletion() {
        return isFlaggedForDeletion;
    }

    public void setFlaggedForDeletion(boolean flaggedForDeletion) {
        isFlaggedForDeletion = flaggedForDeletion;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public Group setFavourite(boolean favourite) {
        this.favourite = favourite;
        return this;
    }

    public int getRating() {
        return rating;
    }

    public Group setRating(int rating) {
        this.rating = rating;
        return this;
    }

    public Group setHasCustomBookOrder(boolean hasCustomBookOrder) {
        this.hasCustomBookOrder = hasCustomBookOrder;
        return this;
    }

    public String getName() {
        return name;
    }

    public Group setSearchUri(String value) {
        searchUri = value;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return grouping == group.grouping &&
                Objects.equals(name, group.name);
    }

    @Override
    // Must be an int32, so we're bound to use Objects.hash
    public int hashCode() {
        return Objects.hash(grouping, name);
    }

    public long uniqueHash() {
        return Helper.hash64((grouping + "." + name).getBytes());
    }

    public static class GroupingConverter implements PropertyConverter<Grouping, Integer> {
        @Override
        public Grouping convertToEntityProperty(Integer databaseValue) {
            if (databaseValue == null) return null;
            return Grouping.searchById(databaseValue);
        }

        @Override
        public Integer convertToDatabaseValue(Grouping entityProperty) {
            return entityProperty == null ? null : entityProperty.getId();
        }
    }
}
