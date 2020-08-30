package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import java.util.List;
import java.util.Objects;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.converter.PropertyConverter;
import io.objectbox.relation.ToMany;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.enums.Grouping;

@Entity
public class Group {

    @Id
    public long id;
    @Convert(converter = GroupingConverter.class, dbType = Integer.class)
    public Grouping grouping;
    public String name;
    @Backlink(to = "group")
    public ToMany<GroupItem> items;
    public ToOne<ImageFile> picture;
    public int order;

    // Needs to be in the DB to keep the information when deletion takes a long time
    // and user navigates away; no need to save that into JSON
    private boolean isBeingDeleted = false;


    public Group() {
    }  // Required for ObjectBox to work

    public Group(@NonNull final Grouping grouping, @NonNull final String name, int order) {
        this.grouping = grouping;
        this.name = name;
        this.order = order;
    }

    public List<Content> getContents() {
        return Stream.of(items).withoutNulls().sortBy(i -> i.order).map(GroupItem::getContent).withoutNulls().toList();
    }

    public boolean isBeingDeleted() {
        return isBeingDeleted;
    }

    public void setIsBeingDeleted(boolean isBeingDeleted) {
        this.isBeingDeleted = isBeingDeleted;
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
    public int hashCode() {
        return Objects.hash(grouping, name);
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
