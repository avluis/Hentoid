package me.devsaki.hentoid.parsers.content;

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

    public abstract Content toContent(@Nonnull String url);
}
