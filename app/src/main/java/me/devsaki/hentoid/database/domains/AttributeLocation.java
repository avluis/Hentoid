package me.devsaki.hentoid.database.domains;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.annotation.Nonnull;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.enums.Site;

@Entity
public class AttributeLocation {

    @Id
    public long id;
    @Convert(converter = Site.SiteConverter.class, dbType = Long.class)
    public Site site;
    public String url;
    public ToOne<Attribute> attribute;

    public AttributeLocation() { // Required by ObjectBox
    }

    AttributeLocation(Site site, String url) {
        this.site = site;
        this.url = url;
    }

    AttributeLocation(@Nonnull DataInputStream input) throws IOException {
        this.site = Site.searchByCode(input.readInt());
        this.url = input.readUTF();
    }


    void saveToStream(DataOutputStream output) throws IOException {
        output.writeInt(null == site ? Site.NONE.getCode() : site.getCode());
        output.writeUTF(url);
    }
}
