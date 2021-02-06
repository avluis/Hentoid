package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.NhentaiParser;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import pl.droidsonroids.jspoon.annotation.Selector;

// NHentai API reference : https://github.com/NHMoeDev/NHentai-android/issues/27
public class NhentaiContent extends BaseContentParser {

    @Selector(value = "#bigcontainer #cover a", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(value = "#cover img", attr = "data-src", defValue = "")
    private String coverUrl;
    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private String title;
    // Fallback value for title (see #449)
    @Selector(value = "#info h1", defValue = "<no title>")
    private String titleAlt;

    @Selector(value = "#info a[href*='/artist']")
    private List<Element> artists;
    @Selector(value = "#info a[href^='/group/']")
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

    @Selector(value = "#thumbnail-container img[data-src]")
    private List<Element> thumbs;


    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.NHENTAI);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;

        if (theUrl.isEmpty()) return result.setStatus(StatusContent.IGNORED);
        if (null == thumbs || thumbs.isEmpty()) return result.setStatus(StatusContent.IGNORED);
        if (theUrl.endsWith("favorite"))
            return result.setStatus(StatusContent.IGNORED); // Fav button

        result.setUrl(theUrl.replace("/g", "").replaceFirst("/1/$", "/"));
        result.setCoverImageUrl(coverUrl);

        String titleDef = title.trim();
        if (titleDef.isEmpty()) titleDef = titleAlt.trim();
        result.setTitle(Helper.removeNonPrintableChars(titleDef));

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, "name", Site.NHENTAI);
        result.addAttributes(attributes);

        List<ImageFile> images = ParseHelper.urlsToImageFiles(NhentaiParser.parseImages(result, thumbs), result.getCoverImageUrl(), StatusContent.SAVED);
        result.setImageFiles(images);
        result.setQtyPages(images.size() - 1);  // Don't count the cover

        return result;
    }

}
