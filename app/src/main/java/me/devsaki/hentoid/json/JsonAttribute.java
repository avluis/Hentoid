package me.devsaki.hentoid.json;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeLocation;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;

class JsonAttribute {

    private String name;
    private AttributeType type;
    private String url = "";

    private JsonAttribute() {
    }

    static JsonAttribute fromEntity(Attribute a, Site site) {
        JsonAttribute result = new JsonAttribute();
        result.name = a.getName();
        result.type = a.getType();
        result.computeUrl(a.getLocations(), site);
        return result;
    }

    private void computeUrl(List<AttributeLocation> locations, Site site) {
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

    public AttributeType getType() { return type; }
}
