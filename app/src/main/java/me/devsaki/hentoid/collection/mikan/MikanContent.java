package me.devsaki.hentoid.collection.mikan;

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
    @Expose
    public long id;
    @Expose
    public String name;
    @Expose
    public String url;
    @Expose
    public String image;
    @Expose
    public List<MikanAttribute> artist = new ArrayList<>();
    @Expose
    public List<MikanAttribute> group = new ArrayList<>();
    @Expose
    public List<MikanAttribute> series = new ArrayList<>();
    @Expose
    public List<MikanAttribute> characters = new ArrayList<>();
    @Expose
    public List<MikanAttribute> tags = new ArrayList<>();
    @Expose
    public MikanAttribute type;
    @Expose
    public MikanAttribute language;
    // TODO
    @Expose
    public Date time;


    public Content toContent() {
        Content result = new Content();

        if (null == url) return result; // Corrupted result


        String[] urlStr = url.split("/");
        result.setUrl("/" + urlStr[urlStr.length - 1]);
        result.setTitle(name);

        AttributeMap attributes = new AttributeMap();

        for (MikanAttribute a : artist)
            attributes.add(new Attribute(AttributeType.ARTIST, a.name, a.url, Site.HITOMI));
        for (MikanAttribute a : group)
            attributes.add(new Attribute(AttributeType.CIRCLE, a.name, a.url, Site.HITOMI));
        for (MikanAttribute a : series)
            attributes.add(new Attribute(AttributeType.SERIE, a.name, a.url, Site.HITOMI));
        for (MikanAttribute a : characters)
            attributes.add(new Attribute(AttributeType.CHARACTER, a.name, a.url, Site.HITOMI));
        for (MikanAttribute a : tags)
            attributes.add(new Attribute(AttributeType.TAG, a.name, a.url, Site.HITOMI));
        if (type != null)
            attributes.add(new Attribute(AttributeType.CATEGORY, type.name, type.url, Site.HITOMI));
        if (language != null)
            attributes.add(new Attribute(AttributeType.LANGUAGE, language.name, language.url, Site.HITOMI));

        result.addAttributes(attributes);
        result.setCoverImageUrl(image);
        result.setUploadDate(time.getTime());

        result.setSite(Site.searchByUrl(url));
        result.setStatus(StatusContent.ONLINE);
        return result;
    }
}
