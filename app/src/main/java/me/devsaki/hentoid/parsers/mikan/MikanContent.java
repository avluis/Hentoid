package me.devsaki.hentoid.parsers.mikan;

import com.google.gson.annotations.Expose;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

public class MikanContent implements Serializable {

    public class MikanAttribute implements Serializable {
        @Expose
        public String name;
        @Expose
        public String url;
    }

    @Expose
    public long id;
    @Expose
    public String name;
    @Expose
    public String url;
    @Expose
    public String image;
    @Expose
    public List<String> images = new ArrayList<>(); // Deprecated ?
    @Expose
    public List<MikanAttribute> artist = new ArrayList<>();
    @Expose
    public List<MikanAttribute> series = new ArrayList<>();
    @Expose
    public List<MikanAttribute> tags = new ArrayList<>();
    @Expose
    public MikanAttribute type;
    @Expose
    public MikanAttribute language;
    // TODO
    @Expose
    public Date time;


    public Content toContent()
    {
        Content result = new Content();

        if (null == url) return result; // Corrupted result


        String[] urlStr = url.split("/");
        result.setUrl("/"+urlStr[urlStr.length-1]);
        result.setTitle(name);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        for (MikanAttribute a : artist) attributes.add(new Attribute(AttributeType.ARTIST, a.name, a.url));
        for (MikanAttribute a : series) attributes.add(new Attribute(AttributeType.SERIE, a.name, a.url));
        for (MikanAttribute a : tags) attributes.add(new Attribute(AttributeType.TAG, a.name, a.url));
        if (type != null) attributes.add(new Attribute(AttributeType.CATEGORY, type.name, type.url));
        if (language != null) attributes.add(new Attribute(AttributeType.LANGUAGE, language.name, language.url));


        // Cover = 1st image of the set -- deprecated ?
        if (images.size() > 0) result.setCoverImageUrl(images.get(0)); else result.setCoverImageUrl(image);

        // TODO

        result.setSite(Site.searchByUrl(url));
        result.setStatus(StatusContent.ONLINE);


        return result;
    }
}
