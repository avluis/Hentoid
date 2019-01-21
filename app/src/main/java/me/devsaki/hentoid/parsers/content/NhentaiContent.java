package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.FileHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

// NHentai API reference : https://github.com/NHMoeDev/NHentai-android/issues/27
public class NhentaiContent {

    @Selector(value = "#download", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private String coverUrl;
    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private String title;

    @Selector(value = "#info a[href*='/artist']")
    private List<Element> artists;
    @Selector(value = "#info a[href*='/group']")
    private List<Element> circles;
    @Selector(value = "#info a[href*='/tag']")
    private List<Element> tags;
    @Selector(value = "#info a[href*='/parody']")
    private List<Element> series;
    @Selector(value = "#info a[href*='/character']")
    private List<Element> characters;
    @Selector(value = "#info a[href*='/language']")
    private List<Element> languages;
    @Selector(value = "#info a[href*='/category']")
    private List<Element> categories;

    @Selector(value = "#thumbnail-container img[data-src]", attr = "data-src")
    private List<String> thumbs;


    public Content toContent() {
        Content result = new Content();

        result.setSite(Site.NHENTAI);
        if (galleryUrl.isEmpty()) return result;

        result.setUrl(galleryUrl.replace("download", "").replace("/g", ""));
        result.setCoverImageUrl(coverUrl);
        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, true);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, true);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, true);

        List<ImageFile> images = new ArrayList<>();
        result.setImageFiles(images);

        String[] coverParts = coverUrl.split("/");
        String mediaId = coverParts[coverParts.length - 2];
        String serverUrl = "https://i.nhentai.net/galleries/" + mediaId + "/"; // We infer the whole book is stored on the same server

        int index = 1;
        for (String s : thumbs) {
            images.add(new ImageFile(index, serverUrl + index + "." + FileHelper.getExtension(s), StatusContent.SAVED)); // We infer actual book page images have the same format as their thumbs
            index++;
        }
        result.setQtyPages(thumbs.size()); // We infer there are as many thumbs as actual book pages on the gallery summary webpage

        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }

}
