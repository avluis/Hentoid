package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;

public class HitomiContent extends BaseContentParser {
    private static final Pattern CANONICAL_URL = Pattern.compile("/galleries/[0-9]+\\.html");

    /*
}
    @Selector(value = "h1 a[href*='/reader/']", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(value = "h1 a[href*='/reader/']", defValue = NO_TITLE)
    private String title;
    @Selector(value = "div.gallery h2 a[href^='/artist']")
    private List<Element> artists;
    @Selector(value = "div.gallery tr a[href^='/group']")
    private List<Element> circles;
    @Selector(value = "div.gallery tr a[href^='/tag']")
    private List<Element> tags;
    @Selector(value = "div.gallery tr a[href^='/serie']")
    private List<Element> series;
    @Selector(value = "div.gallery tr a[href^='/character']")
    private List<Element> characters;
    @Selector(value = "div.gallery tr a[href^='/index-']")
    private List<Element> languages;
    @Selector(value = "div.gallery tr a[href^='/type']")
    private List<Element> categories;

     */

    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        // Hitomi now uses an empty template that is populated by Javascript -> parsing is entirely done by HitomiParser
        /*
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);
         */
        content.setSite(Site.HITOMI);

        // If given URL is canonical, set it as is
        if (CANONICAL_URL.matcher(url).find()) {
            content.setUrl(url.replace(Site.HITOMI.getUrl(), "").replace("/reader", "").replace("/galleries", ""));
        } else { // If not, extract unique site ID (hitomi.la/category/stuff-<ID>.html#stuff)...
            int pathEndIndex = url.lastIndexOf("?");
            if (-1 == pathEndIndex) pathEndIndex = url.lastIndexOf("#");
            if (-1 == pathEndIndex) pathEndIndex = url.length();
            int firstIndex = url.lastIndexOf("-", pathEndIndex);
            int lastIndex = url.lastIndexOf(".", pathEndIndex);
            if (-1 == lastIndex) lastIndex = pathEndIndex;
            String uniqueId = url.substring(firstIndex + 1, lastIndex);

            content.setUniqueSiteId(uniqueId);

            // ...and forge canonical URL
            content.setUrl("/" + uniqueId + ".html");
        }

/*        content.setTitle(StringHelper.removeNonPrintableChars(title));

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, Site.HITOMI);
*/
        content.putAttributes(new AttributeMap());

        if (updateImages) content.setImageFiles(Collections.emptyList());

        return content;
    }
}
