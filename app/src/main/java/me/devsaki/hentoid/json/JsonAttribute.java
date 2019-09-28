package me.devsaki.hentoid.json;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeLocation;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;

public class JsonAttribute {

    public String name;
    public AttributeType type;
    public String url = "";

    JsonAttribute(Attribute a) {
        this.name = a.getName();
        this.type = a.getType();
    }

    void computeUrl(List<AttributeLocation> locations, Site site) {
        for (AttributeLocation location : locations) {
            if (location.site.equals(site)) {
                url = location.url;
                return;
            }
        }
        url = ""; // Field shouldn't be null
    }

    Attribute toEntity(Site site) {
        return new Attribute(type, name, url, site);
    }
}
