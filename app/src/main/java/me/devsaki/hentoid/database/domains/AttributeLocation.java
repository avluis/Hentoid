package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.enums.Site;

@Entity
// This is a dumb struct class, nothing more
@SuppressWarnings("squid:S1104")
public class AttributeLocation {

    @Id
    public long id;
    @Convert(converter = Site.SiteConverter.class, dbType = Long.class)
    public Site site;
    public String url;
    public ToOne<Attribute> attribute;

    public AttributeLocation() { // Required by ObjectBox when an alternate constructor exists
    }

    AttributeLocation(Site site, String url) {
        this.site = site;
        this.url = url;
    }

    AttributeLocation(@NonNull DataInputStream input) throws IOException {
        this.site = Site.searchByCode(input.readInt());
        this.url = input.readUTF();
    }


    void saveToStream(DataOutputStream output) throws IOException {
        output.writeInt(null == site ? Site.NONE.getCode() : site.getCode());
        output.writeUTF(url);
    }
}
