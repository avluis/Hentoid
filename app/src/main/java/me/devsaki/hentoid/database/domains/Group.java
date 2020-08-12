package me.devsaki.hentoid.database.domains;

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

    public Group() {
    }  // Required for ObjectBox to work

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
