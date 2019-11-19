package me.devsaki.hentoid.parsers.content;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;

public interface ContentParser {
    Content toContent(@Nonnull String url);
}
