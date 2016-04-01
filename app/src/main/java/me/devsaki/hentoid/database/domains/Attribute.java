package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import me.devsaki.hentoid.enums.AttributeType;

/**
 * Created by DevSaki on 09/05/2015.
 * Attribute builder
 */
public class Attribute {

    @Expose
    private String url;
    @Expose
    private String name;
    @Expose
    private AttributeType type;

    public Attribute() {
    }

    public Integer getId() {
        return url.hashCode();
    }

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
}
