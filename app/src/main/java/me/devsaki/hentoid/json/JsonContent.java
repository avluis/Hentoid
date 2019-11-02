package me.devsaki.hentoid.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;

public class JsonContent {

    private String url;
    private String title;
    private String author;
    private String coverImageUrl;
    private Integer qtyPages;
    private long uploadDate;
    private long downloadDate;
    private StatusContent status;
    private Site site;
    private boolean favourite;
    private long reads;
    private long lastReadDate;
    private int lastReadPageIndex;

    private Map<AttributeType, List<JsonAttribute>> attributes;
    private List<JsonImageFile> imageFiles = new ArrayList<>();

    private JsonContent() {
    }


    private void addAttribute(JsonAttribute attributeItem) {
        if (null == attributeItem) return;

        List<JsonAttribute> list;
        AttributeType type = attributeItem.getType();

        if (attributes.containsKey(type)) {
            list = attributes.get(type);
        } else {
            list = new ArrayList<>();
            attributes.put(type, list);
        }
        if (list != null) list.add(attributeItem);
    }

    public static JsonContent fromEntity(Content c) {
        JsonContent result = new JsonContent();
        result.url = c.getUrl();
        result.title = c.getTitle();
        result.author = c.getAuthor();
        result.coverImageUrl = c.getCoverImageUrl();
        result.qtyPages = c.getQtyPages();
        result.uploadDate = c.getUploadDate();
        result.downloadDate = c.getDownloadDate();
        result.status = c.getStatus();
        result.site = c.getSite();
        result.favourite = c.isFavourite();
        result.reads = c.getReads();
        result.lastReadDate = c.getLastReadDate();
        result.lastReadPageIndex = c.getLastReadPageIndex();

        if (c.getImageFiles() != null)
            for (ImageFile img : c.getImageFiles())
                result.imageFiles.add(JsonImageFile.fromEntity(img));

        result.attributes = new HashMap<>();
        for (Attribute a : c.getAttributes()) {
            JsonAttribute attr = JsonAttribute.fromEntity(a, c.getSite());
            result.addAttribute(attr);
        }
        return result;
    }

    public Content toEntity() {
        Content result = new Content();

        if (null == site) site = Site.NONE;
        result.setSite(site);
        result.setUrl(url);
        result.setTitle(title);
        result.setAuthor(author);
        result.setCoverImageUrl(coverImageUrl);
        result.setQtyPages(qtyPages);
        result.setUploadDate(uploadDate);
        result.setDownloadDate(downloadDate);
        result.setStatus(status);
        result.setFavourite(favourite);
        result.setReads(reads);
        result.setLastReadDate(lastReadDate);
        result.setLastReadPageIndex(lastReadPageIndex);

        if (attributes != null) {
            result.clearAttributes();
            for (List<JsonAttribute> jsonAttrList : attributes.values()) {
                List<Attribute> attrList = new ArrayList<>();
                for (JsonAttribute attr : jsonAttrList) attrList.add(attr.toEntity(site));
                result.addAttributes(attrList);
            }
        }
        if (imageFiles != null) {
            List<ImageFile> imgs = new ArrayList<>();
            for (JsonImageFile img : imageFiles) imgs.add(img.toEntity(imageFiles.size()));
            result.setImageFiles(imgs);
        }

        result.populateAuthor();
        result.populateUniqueSiteId();
        return result;
    }
}
