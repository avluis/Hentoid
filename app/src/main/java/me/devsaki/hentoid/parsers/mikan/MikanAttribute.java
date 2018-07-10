package me.devsaki.hentoid.parsers.mikan;

import com.google.gson.annotations.Expose;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;

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

        return result;
    }
}
