package me.devsaki.hentoid.parsers.mikan;

import com.google.gson.annotations.Expose;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.AttributeMap;

public class MikanContent implements Serializable {

    public class MikanProperty implements Serializable {
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
    public List<String> images = new ArrayList<>();
    @Expose
    public List<MikanProperty> artist = new ArrayList<>();
    // TODO
    @Expose
    public Date time;


    public Content toContent()
    {
        Content result = new Content();

        result.setUrl(url);
        result.setTitle(name);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        attributes.add(new Attribute(AttributeType.ARTIST, name, url));
        if (images.size() > 0) result.setCoverImageUrl(images.get(0));

        // TODO

        result.setSite(Site.searchByUrl(url));


        return result;
    }
}
