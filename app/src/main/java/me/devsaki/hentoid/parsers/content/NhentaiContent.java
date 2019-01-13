package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

// NHentai API reference : https://github.com/NHMoeDev/NHentai-android/issues/27
public class NhentaiContent {

    @Selector(value = "#download", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private String coverUrl;
    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private String title;

    @Selector(value = "#info div:not(.tag-container)", defValue = "")
    private List<String> nbPages;

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


    public Content toContent() {
        Content result = new Content();

        result.setSite(Site.NHENTAI);
        result.setUrl(galleryUrl.replace("download", "").replace("/g",""));
        result.setCoverImageUrl(coverUrl);
        result.setTitle(title);

        int qtyPages = 0;
        for (String s : nbPages) {
            if (s.contains(" pages")) {
                qtyPages = Integer.parseInt(s.replace(" pages", ""));
                break;
            }
        }
        result.setQtyPages(qtyPages);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, true);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, true);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, true);

        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }

}
