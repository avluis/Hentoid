package me.devsaki.hentoid.parsers.content;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

public class EHentaiGalleryMetadata {

    public String gid;
    public String token;
    public String archiver_key;
    public String title;
    public String title_jpn;
    public String category;
    public String thumb;
    public String uploader;
    public String posted;
    public String filecount;
    public String filesize;
    public boolean expunged;
    public double rating;
    public String torrentcount;
    public List<String> tags;


    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.EHENTAI);

        result.setUrl("/" + gid + "/" + token) // The rest will not be useful anyway because of temporary keys
                .setCoverImageUrl(thumb)
                .setTitle(title)
                .setQtyPages(Integer.parseInt(filecount))
                .setStatus(StatusContent.SAVED);

        AttributeMap attributes = new AttributeMap();
        String[] tagParts;
        AttributeType type;
        String name;

        for (String s : tags) {
            tagParts = s.split(":");
            if (1 == tagParts.length) {
                type = AttributeType.TAG;
                name = s;
            } else {
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

            attributes.add(new Attribute(type, name, type.name() + "/" + name, Site.EHENTAI));
        }
        result.addAttributes(attributes);

        return result;
    }

}
