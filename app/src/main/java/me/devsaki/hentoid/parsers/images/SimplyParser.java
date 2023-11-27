package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.sources.SimplyGalleryMetadata;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.network.HttpHelper;

public class SimplyParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();
        processedUrl = content.getGalleryUrl();

        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

        // 1. Scan the gallery page for viewer URL (can't be deduced)
        String viewerUrl = null;
        Document doc = getOnlineDocument(content.getGalleryUrl(), headers, Site.SIMPLY.useHentoidAgent(), Site.SIMPLY.useWebviewAgent());
        if (doc != null) {
            Element page = doc.select(".image-wrapper").first();
            if (null == page) return result;

            Element parent = page.parent();
            while (parent != null && !parent.is("a")) parent = parent.parent();
            if (null == parent) return result;

            viewerUrl = HttpHelper.fixUrl(parent.attr("href"), Site.SIMPLY.getUrl());
        }
        if (null == viewerUrl) return result;

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw new PreparationInterruptedException();

        // 2. Get the metadata on the viewer page
        doc = getOnlineDocument(viewerUrl, headers, Site.SIMPLY.useHentoidAgent(), Site.SIMPLY.useWebviewAgent());
        if (doc != null) {
            Element jsonData = doc.select("body script[type='application/json']").first();
            if (null == jsonData) return result;

            String data = jsonData.data();
            if (!data.contains("thumb")) return result;

            SimplyGalleryMetadata meta = JsonHelper.jsonToObject(data, SimplyGalleryMetadata.class);
            result = meta.getPageUrls();
        }

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw new PreparationInterruptedException();

        return result;
    }

    @Override
    protected List<String> parseImages(@NonNull String chapterUrl, String downloadParams, List<Pair<String, String>> headers) throws Exception {
        // Nothing; no chapters for this source
        return null;
    }

    @Override
    protected boolean isChapterUrl(@NonNull String url) {
        return false;
    }
}
