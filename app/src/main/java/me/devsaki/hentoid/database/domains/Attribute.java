package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;
import io.objectbox.annotation.Unique;
import me.devsaki.hentoid.enums.AttributeType;

/**
 * Created by DevSaki on 09/05/2015.
 * Attribute builder
 */
@Entity
public class Attribute {

    private final static int ATTRIBUTE_FILE_VERSION = 1;

    @Id
    private long id;
    @Expose
//    @Unique
    private String url;
    @Expose
    private String name;
    @Expose
    @Convert(converter = AttributeType.AttributeTypeConverter.class, dbType = Integer.class)
    private AttributeType type;
    @Transient
    private int count;
    @Transient
    private int externalId = 0;

    public Attribute() {
    }

    public Attribute(AttributeType type, String name, String url) {
        this.type = type;
        this.name = name;
        this.url = url;
    }
    // TODO check behaviour
/*
    public Integer getId() {
        return (0 == externalId) ? url.hashCode() : externalId;
    }
*/
    public long getId() { return this.id; }
    public void setId(long id ) { this.id = id; }

    public String getUrl() {
        return url;
    }

    public Attribute setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getName() {
        return name;
    }

    public Attribute setName(String name) {
        this.name = name;
        return this;
    }

    public AttributeType getType() {
        return type;
    }

    public Attribute setType(AttributeType type) {
        this.type = type;
        return this;
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

    @Override
    public String toString() {
        return getName();
    }

    public void saveToStream(DataOutputStream output) throws IOException {
        output.writeInt(ATTRIBUTE_FILE_VERSION);
        output.writeUTF(null == url ? "" : url);
        output.writeUTF(name);
        output.writeInt(type.getCode());
        output.writeInt(count);
        output.writeInt(externalId);
    }

    public Attribute loadFromStream(DataInputStream input) throws IOException {
        input.readInt(); // file version
        url = input.readUTF();
        name = input.readUTF();
        type = AttributeType.searchByCode(input.readInt());
        count = input.readInt();
        externalId = input.readInt();
        return this;
    }


    public static final Comparator<Attribute> NAME_COMPARATOR = (a, b) -> a.getName().compareTo(b.getName());

    public static final Comparator<Attribute> COUNT_COMPARATOR = (a, b) -> {
        return Long.compare(a.getCount(), b.getCount()) * -1; /* Inverted - higher count first */
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Attribute attribute = (Attribute) o;

        if ((externalId != 0 && attribute.externalId != 0) && externalId != attribute.externalId)
            return false;
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
