package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.Transient;
import io.objectbox.relation.ToMany;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

/**
 * Attribute builder
 */
@Entity
public class Attribute {

    @Id
    private long id;
    @Index
    private String name;
    @Index
    @Convert(converter = AttributeType.AttributeTypeConverter.class, dbType = Integer.class)
    private AttributeType type;
    @Backlink(to = "attribute")
    private ToMany<AttributeLocation> locations; // One entry per site
    private ToOne<Group> group; // Associated group

    // Runtime attributes; no need to expose them nor to persist them
    @Transient
    private boolean excluded = false;
    @Transient
    private boolean isNew = false;
    @Transient
    private int count = 0;
    @Transient
    private int externalId = 0;
    @Backlink(to = "attributes") // backed by the to-many relation in Content
    public ToMany<Content> contents;
    @Transient
    private String displayName = "";
    @Transient
    private long uniqueHash = 0;    // cached value of uniqueHash

    // WARNING : Update copy constructor when adding attributes


    public Attribute() { // Required by ObjectBox when an alternate constructor exists
    }

    public Attribute(Attribute data) {
        this.id = data.id;
        this.name = data.name;
        this.type = data.type;
        data.locations.clear();
        data.locations.addAll(this.locations); // this isn't a deep copy
        data.group.setTargetId(this.group.getTargetId());

        this.excluded = data.excluded;
        this.isNew = data.isNew;
        this.count = data.count;
        this.externalId = data.externalId;
        data.contents.clear();
        data.contents.addAll(this.contents); // this isn't a deep copy
        this.displayName = data.displayName;
        this.uniqueHash = data.uniqueHash;
    }

    public Attribute(@Nonnull AttributeType type, @Nonnull String name) {
        this.type = type;
        this.name = name;
    }

    public Attribute(@Nonnull AttributeType type, @Nonnull String name, @Nonnull String url, @Nonnull Site site) {
        this.type = type;
        this.name = name;
        computeLocation(site, url);
    }

    public Attribute(@Nonnull DataInputStream input) throws IOException {
        input.readInt(); // file version
        name = input.readUTF();
        type = AttributeType.Companion.searchByCode(input.readInt());
        count = input.readInt();
        externalId = input.readInt();
        int nbLocations = input.readInt();
        for (int i = 0; i < nbLocations; i++) locations.add(new AttributeLocation(input));
    }

    public long getId() {
        return (0 == externalId) ? this.id : this.externalId;
    }

    public Attribute setId(long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(@Nonnull String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName.isEmpty() ? name : displayName;
    }

    public void setDisplayName(@Nonnull String displayname) {
        this.displayName = displayname;
    }

    public AttributeType getType() {
        return type;
    }

    public Attribute setExcluded(boolean toExclude) {
        excluded = toExclude;
        return this;
    }

    public boolean isExcluded() {
        return excluded;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }

    public void setType(@Nonnull AttributeType type) {
        this.type = type;
    }

    public ToMany<AttributeLocation> getLocations() {
        return locations;
    }

    public void setLocations(ToMany<AttributeLocation> locations) {
        this.locations = locations;
    }

    public int getCount() {
        return count;
    }

    public Attribute setCount(int count) {
        this.count = count;
        return this;
    }

    public ToOne<Group> getGroup() {
        return group;
    }

    @Nullable
    public Group getLinkedGroup() {
        return (group != null && !group.isNull()) ? group.getTarget() : null;
    }

    public void putGroup(@NonNull Group group) {
        this.group.setAndPutTarget(group);
    }

    public Attribute setExternalId(int id) {
        this.externalId = id;
        return this;
    }

    Attribute computeLocation(Site site, String url) {
        locations.add(new AttributeLocation(site, url));
        return this;
    }

    public void addLocationsFrom(Attribute sourceAttribute) {
        for (AttributeLocation sourceLocation : sourceAttribute.getLocations()) {
            boolean foundSite = false;
            for (AttributeLocation loc : this.locations) {
                if (sourceLocation.site.equals(loc.site)) {
                    foundSite = true;
                    if (!sourceLocation.url.equals(loc.url))
                        Timber.w("'%s' Attribute location mismatch : current '%s' vs. add's target '%s'", this.name, loc.url, sourceLocation.url);
                    break;
                }
            }
            if (!foundSite) this.locations.add(sourceLocation);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return getType().name().toLowerCase(Locale.ROOT) + ":" + getName();
    }

    // Hashcode (and by consequence equals) has to take into account fields that get visually updated on the app UI
    // If not done, FastAdapter's PagedItemListImpl cache won't detect changes to the object
    // and items won't be visually updated on screen
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Attribute attribute = (Attribute) o;

        if ((externalId != 0 && attribute.externalId != 0) && externalId != attribute.externalId)
            return false;
        if ((id != 0 && attribute.id != 0) && id != attribute.id) return false;
        if (!name.equals(attribute.name)) return false;
        return type == attribute.type;
    }

    @Override
    public int hashCode() {
        // Must be an int32, so we're bound to use Objects.hash
        long idComp = id;
        if (externalId != 0) idComp = externalId;
        return Objects.hash(getName(), getType(), idComp);
    }

    public long uniqueHash() {
        if (0 == uniqueHash) {
            long idComp = id;
            if (externalId != 0) idComp = externalId;
            uniqueHash = Helper.hash64((idComp + "." + name + "." + type.getCode()).getBytes());
        }
        return uniqueHash;
    }
}
