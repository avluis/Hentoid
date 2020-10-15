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

    // Useful for unit tests not to fail on the CI environment
    private void initObjectBoxRelations() {
        this.attribute = new ToOne<>(this, AttributeLocation_.attribute);
    }

    // No-arg constructor required by ObjectBox
    public AttributeLocation() {
        initObjectBoxRelations();
    }

    AttributeLocation(Site site, String url) {
        initObjectBoxRelations();
        this.site = site;
        this.url = url;
    }

    AttributeLocation(@Nonnull DataInputStream input) throws IOException {
        initObjectBoxRelations();
        this.site = Site.searchByCode(input.readInt());
        this.url = input.readUTF();
    }
}
