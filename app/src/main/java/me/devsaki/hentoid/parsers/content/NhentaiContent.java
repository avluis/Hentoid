package me.devsaki.hentoid.parsers.content;

import com.google.gson.annotations.Expose;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

// NHentai API reference : https://github.com/NHMoeDev/NHentai-android/issues/27
public class NhentaiContent {

    @Expose
    public Long id;
    @Expose
    public String media_id;
    @Expose
    public NHentaiTitle title;
    @Expose
    public NHentaiImages images;
    @Expose
    public String scanlator;
    @Expose
    public Long upload_date;
    @Expose
    public List<NHentaiTag> tags;
    @Expose
    public Integer num_pages;
    @Expose
    public Integer num_favorites;


    public Content toContent() {
        Content result = new Content();

        String extension = images.cover.t;
        switch (extension) {
            case "j":
                extension = "jpg";
                break;
            case "p":
                extension = "png";
                break;
            default:
                extension = "gif";
                break;
        }
        String coverImageUrl = "https://t.nhentai.net/galleries/" + media_id + "/cover." + extension;

        result.setUrl("/" + id.toString() + "/")
                .setCoverImageUrl(coverImageUrl)
                .setTitle(title.pretty)
                .setQtyPages(num_pages)
                .setStatus(StatusContent.SAVED)
                .setUploadDate(upload_date)
                .setSite(Site.NHENTAI);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        for (NHentaiTag tag : tags) {
            attributes.add(tag.toAttribute());
        }

        result.populateAuthor();

        return result;
    }

    public class NHentaiTitle {
        @Expose
        public String english;
        @Expose
        public String japanese;
        @Expose
        public String pretty;
    }

    public class NHentaiImages {
        @Expose
        public List<NHentaiImage> pages;
        @Expose
        public NHentaiImage cover;
        @Expose
        public NHentaiImage thumbnail;
    }

    public class NHentaiImage {
        @Expose
        public String t;
        @Expose
        public String w;
        @Expose
        public String h;
    }

    public class NHentaiTag {
        @Expose
        public Integer id;
        @Expose
        public String type;
        @Expose
        public String name;
        @Expose
        public String url;
        @Expose
        public Integer count;

        public Attribute toAttribute() {
            Attribute result = new Attribute();

            AttributeType theType = AttributeType.TAG;

            switch (type) {
                case "artist":
                    theType = AttributeType.ARTIST;
                    break;
                case "character":
                    theType = AttributeType.CHARACTER;
                    break;
                case "parody":
                    theType = AttributeType.SERIE;
                    break;
                case "language":
                    theType = AttributeType.LANGUAGE;
                    break;
                case "tag":
                    theType = AttributeType.TAG;
                    break;
                case "group":
                    theType = AttributeType.CIRCLE;
                    break;
                case "category":
                    theType = AttributeType.CATEGORY;
                    break;
                default: // do nothing
                    break;
            }

            result.setName(name)
                    .setUrl(url)
                    .setCount(count)
                    .setType(theType);

            return result;
        }
    }

}
