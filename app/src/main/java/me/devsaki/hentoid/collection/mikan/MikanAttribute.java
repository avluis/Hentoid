package me.devsaki.hentoid.collection.mikan;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;

public class MikanAttribute {
    // Published by both Collection and Attributes endpoints
    public String name;

    // Published by Collection (book search, recent) endpoints
    public String url;

    // Published by Attributes endpoints
    public int id;
    public int count;
    public String type;

    Attribute toAttribute() {
        AttributeType attrType;
        switch (type) {
            case "language":
                attrType = AttributeType.LANGUAGE;
                break;
            case "character":
                attrType = AttributeType.CHARACTER;
                break;
            case "artist":
                attrType = AttributeType.ARTIST;
                break;
            case "group":
                attrType = AttributeType.CIRCLE;
                break;
            default:
                attrType = AttributeType.TAG;
        }
        Attribute result = new Attribute(attrType, name, url, Site.HITOMI);
        result.setCount(count);
        result.setExternalId(id);

        return result;
    }
}
