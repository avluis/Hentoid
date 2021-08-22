package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;

public interface ContentParser {
    String getCanonicalUrl();
    Content toContent(@Nonnull String url);
    Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages);
}
