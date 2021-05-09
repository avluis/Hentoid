package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

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
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.util.StringHelper;
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


    public Content update(@NonNull final Content content, @Nonnull String url) {
        content.setSite(Site.NHENTAI);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;

        if (theUrl.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);
        if (null == thumbs || thumbs.isEmpty())
            return new Content().setStatus(StatusContent.IGNORED);
        if (theUrl.endsWith("favorite"))
            return new Content().setStatus(StatusContent.IGNORED); // Fav button

        content.setUrl(theUrl.replace("/g", "").replaceFirst("/1/$", "/"));
        content.setCoverImageUrl(coverUrl);

        String titleDef = title.trim();
        if (titleDef.isEmpty()) titleDef = titleAlt.trim();
        content.setTitle(StringHelper.removeNonPrintableChars(titleDef));

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, "name", Site.NHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, "name", Site.NHENTAI);
        content.putAttributes(attributes);

        List<ImageFile> images = ParseHelper.urlsToImageFiles(NhentaiParser.parseImages(content, thumbs), content.getCoverImageUrl(), StatusContent.SAVED);
        content.setImageFiles(images);
        content.setQtyPages(images.size() - 1);  // Don't count the cover

        return content;
    }

}
