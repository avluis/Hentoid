package me.devsaki.hentoid.collection.mikan;

import com.google.gson.annotations.Expose;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;

public class MikanAttribute {
    // Published by both Collection and Attributes endpoints
    @Expose
    public String name;

    // Published by Collection (book search, recent) endpoints
    @Expose
    public String url;

    // Published by Attributes endpoints
    @Expose
    public int id;
    @Expose
    public int count;
    @Expose
    public String type;

    public Attribute toAttribute()
    {
        Attribute result = new Attribute();

        result.setName(name);
        result.setUrl(url);
        result.setCount(count);
        result.setExternalId(id);

        AttributeType type;
        switch (this.type) {
            case "language" : type = AttributeType.LANGUAGE; break;
            case "character" : type = AttributeType.CHARACTER; break;
            case "artist" : type = AttributeType.ARTIST; break;
            case "group" : type = AttributeType.CIRCLE; break;
            default : type = AttributeType.TAG;
        }
        result.setType(type);

        return result;
    }
}
