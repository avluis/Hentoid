package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import pl.droidsonroids.jspoon.annotation.Selector;

public abstract class BaseContentParser implements ContentParser {

    protected static final String NO_TITLE = "<no title>";

    @Selector(value = "head [rel=canonical]", attr = "href", defValue = "")
    protected String canonicalUrl;

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public Content toContent(@Nonnull String url) {
        return update(new Content(), url);
    }

    public abstract Content update(@NonNull final Content content, @Nonnull String url);
}
