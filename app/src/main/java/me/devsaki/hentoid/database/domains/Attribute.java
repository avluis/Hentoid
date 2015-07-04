package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import me.devsaki.hentoid.database.contants.AttributeTable;
import me.devsaki.hentoid.database.enums.AttributeType;

/**
 * Created by DevSaki on 09/05/2015.
 */
public class Attribute extends AttributeTable {

    @Expose
    private String url;
    @Expose
    private String name;
    @Expose
    private AttributeType type;

    public Integer getId() {
        return url.hashCode();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AttributeType getType() {
        return type;
    }

    public void setType(AttributeType type) {
        this.type = type;
    }
}
