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

    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        // Hitomi now uses an empty template that is populated by Javascript -> parsing is entirely done by HitomiParser
        content.setSite(Site.HITOMI);

        // If given URL is canonical, set it as is
        if (CANONICAL_URL.matcher(url).find()) {
            content.setRawUrl(url);
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
        content.putAttributes(new AttributeMap());

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }
}
