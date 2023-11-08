package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;

public class AnchiraContent extends BaseContentParser {
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        // Hitomi now uses an empty template that is populated by Javascript -> parsing is entirely done by HitomiParser
        content.setSite(Site.ANCHIRA);

        content.setRawUrl(url);
        content.putAttributes(new AttributeMap());

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }
}
