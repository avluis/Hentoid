package me.devsaki.hentoid.parsers.content;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import pl.droidsonroids.jspoon.annotation.Selector;

public class DummyContent implements ContentParser {

    @Selector("div.info h1:first-child")
    private String title;

    @Nullable
    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.NONE).setStatus(StatusContent.IGNORED);

        return result;
    }
}
