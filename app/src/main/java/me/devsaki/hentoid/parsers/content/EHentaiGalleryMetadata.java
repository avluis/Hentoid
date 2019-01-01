package me.devsaki.hentoid.parsers.content;

import com.google.gson.annotations.Expose;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

import static me.devsaki.hentoid.enums.Site.NHENTAI;

public class EHentaiGalleryMetadata {

    @Expose
    public String gid;
    @Expose
    public String token;
    @Expose
    public String archiver_key;
    @Expose
    public String title;
    @Expose
    public String title_jpn;
    @Expose
    public String category;
    @Expose
    public String thumb;
    @Expose
    public String uploader;
    @Expose
    public String posted;
    @Expose
    public String filecount;
    @Expose
    public String filesize;
    @Expose
    public boolean expunged;
    @Expose
    public double rating;
    @Expose
    public String torrentcount;
    @Expose
    public List<String> tags;


    public Content toContent()
    {
        Content result = new Content();

        result.setUrl("/" + gid + "/" + token) // The rest will not be useful anyway because of temporary keys
                .setCoverImageUrl(thumb)
                .setTitle(title)
                .setQtyPages(Integer.parseInt(filecount))
                .setStatus(StatusContent.SAVED)
                .setSite(Site.EHENTAI);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        String[] tagParts;
        AttributeType type;
        String name;

        for (String s : tags)
        {
            tagParts = s.split(":");
            if (1 == tagParts.length) {
                type = AttributeType.TAG;
                name = s;
            }
            else
            {
                name = tagParts[1];
                switch (tagParts[0]) {
                    case "parody":
                        type = AttributeType.SERIE;
                        break;
                    case "character":
                        type = AttributeType.CHARACTER;
                        break;
                    case "language":
                        type = AttributeType.LANGUAGE;
                        break;
                    case "artist":
                        type = AttributeType.ARTIST;
                        break;
                    default:
                        type = AttributeType.TAG;
                        name = s;
                        break;
                }
            }

            attributes.add(new Attribute(type, name, type.name()+"/"+name));
        }

        return result;
    }

}
