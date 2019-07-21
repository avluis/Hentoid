package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import com.google.gson.annotations.Expose;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;

import javax.annotation.Nonnull;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.Transient;
import io.objectbox.relation.ToMany;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import timber.log.Timber;

/**
 * Created by DevSaki on 09/05/2015.
 * Attribute builder
 */
@Entity
public class Attribute {

    private static final int ATTRIBUTE_FILE_VERSION = 1;

    @Id
    private long id;
    @Expose
    @Index
    private String name;
    @Expose
    @Index
    @Convert(converter = AttributeType.AttributeTypeConverter.class, dbType = Integer.class)
    private AttributeType type;
    @Expose(serialize = false, deserialize = false)
    @Backlink(to = "attribute")
    private ToMany<AttributeLocation> locations; // One entry per site

    // Runtime attributes; no need to expose them nor to persist them
    @Transient
    private int count;
    @Transient
    private int externalId = 0;
    @Backlink(to = "attributes") // backed by the to-many relation in Content
    public ToMany<Content> contents;

    // Kept for retro-compatibility with contentV2.json Hentoid files
    @Transient
    @Expose
    private String url;


    public Attribute() {
    } // No-arg constructor required by ObjectBox

    public Attribute(@Nonnull AttributeType type, @Nonnull String name) {
        this.type = type;
        this.name = name;
        this.url = "";
    }

    public Attribute(@Nonnull AttributeType type, @Nonnull String name, @Nonnull String url, @Nonnull Site site) {
        this.type = type;
        this.name = name;
        this.url = url;
        computeLocation(site);
    }

    public Attribute(@Nonnull DataInputStream input) throws IOException {
        input.readInt(); // file version
        name = input.readUTF();
        type = AttributeType.searchByCode(input.readInt());
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

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public void setName(@Nonnull String name) {
        this.name = name;
    }

    public AttributeType getType() {
        return type;
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

    public Attribute setExternalId(int id) {
        this.externalId = id;
        return this;
    }

    void computeUrl(Site site) {
        for (AttributeLocation location : locations) {
            if (location.site.equals(site)) {
                url = location.url;
                return;
            }
        }
        url = ""; // Field shouldn't be null
    }

    Attribute computeLocation(Site site) {
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
        return getName();
    }

    public String formatLabel() {
        return String.format("%s %s", getName(), getCount() > 0 ? "(" + getCount() + ")" : "");
    }

    public void saveToStream(DataOutputStream output) throws IOException {
        output.writeInt(ATTRIBUTE_FILE_VERSION);
        output.writeUTF(name);
        output.writeInt(type.getCode());
        output.writeInt(count);
        output.writeInt(externalId);
        output.writeInt(locations.size());
        for (AttributeLocation location : locations) {
            location.saveToStream(output);
        }
    }

    public static final Comparator<Attribute> NAME_COMPARATOR = (a, b) -> a.getName().compareTo(b.getName());

    public static final Comparator<Attribute> COUNT_COMPARATOR = (a, b) -> Long.compare(a.getCount(), b.getCount()) * -1; /* Inverted - higher count first */

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
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + externalId;
        return result;
    }
}
