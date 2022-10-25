package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.SimplyContentMetadata;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

/**
 * Parser for Simply Hentai content data served by its API
 */
public class SimplyApiContent extends BaseContentParser {

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        if (url.contains("api.simply-hentai.com") && !url.endsWith("/status")) { // Triggered by an API request
            List<Pair<String, String>> headers = new ArrayList<>();
            ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

            try {
                Document doc = HttpHelper.getOnlineDocument(url, headers, Site.SIMPLY.useHentoidAgent(), Site.SIMPLY.useWebviewAgent());
                if (doc != null) {
                    SimplyContentMetadata metadata = JsonHelper.jsonToObject(doc.body().ownText(), SimplyContentMetadata.class);
                    if (metadata != null) return metadata.update(content, updateImages);
                }
            } catch (IOException e) {
                Timber.e(e, "Error parsing content from API.");
            }
        }
        return new Content().setSite(Site.SIMPLY).setStatus(StatusContent.IGNORED);
    }
}
