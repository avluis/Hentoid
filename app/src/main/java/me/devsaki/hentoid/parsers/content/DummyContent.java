package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;

public class DummyContent extends BaseContentParser {

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url) {
        return content.setSite(Site.NONE).setStatus(StatusContent.IGNORED);
    }
}
