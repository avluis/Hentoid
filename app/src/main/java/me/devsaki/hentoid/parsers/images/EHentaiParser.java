package me.devsaki.hentoid.parsers.images;

import android.util.Pair;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.network.HttpHelper;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

public class EHentaiParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();

    private boolean processHalted = false;


    public List<ImageFile> parseImageList(@NonNull Content content) throws Exception {
        EventBus.getDefault().register(this);

        try {
            List<ImageFile> result = new ArrayList<>();
            boolean useHentoidAgent = Site.EHENTAI.canKnowHentoidAgent();
            Map<String, String> downloadParams = new HashMap<>();
            int order = 1;

            /*
             * 1- Detect the number of pages of the gallery
             *
             * 2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
             *
             * 3- Open all pages and grab the URL of the displayed image
             */

            // 1- Detect the number of pages of the gallery
            Element e;
            List<Pair<String, String>> headers = new ArrayList<>();
            headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, "nw=1")); // nw=1 (always) avoids the Offensive Content popup (equivalent to clicking the "Never warn me again" link)
            Document doc = getOnlineDocument(content.getGalleryUrl(), headers, useHentoidAgent);
            if (doc != null) {
                Elements elements = doc.select("table.ptt a");
                if (null == elements || elements.isEmpty()) return result;

                int tabId = (1 == elements.size()) ? 0 : elements.size() - 2;
                int nbGalleryPages = Integer.parseInt(elements.get(tabId).text());

                progress.start(nbGalleryPages + content.getQtyPages());

                // 2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
                List<String> pageUrls = new ArrayList<>();

                fetchPageUrls(doc, pageUrls);

                if (nbGalleryPages > 1) {
                    for (int i = 1; i < nbGalleryPages && !processHalted; i++) {
                        doc = getOnlineDocument(content.getGalleryUrl() + "/?p=" + i, headers, useHentoidAgent);
                        if (doc != null) fetchPageUrls(doc, pageUrls);
                        progress.advance();
                    }
                }

                // 3- Open all pages and
                //    - grab the URL of the displayed image
                //    - grab the alternate URL of the "Click here if the image fails loading" link
                result.add(ImageFile.newCover(content.getCoverImageUrl(), StatusContent.SAVED));
                ImageFile img;
                for (String pageUrl : pageUrls) {
                    if (processHalted) break;
                    doc = getOnlineDocument(pageUrl, headers, useHentoidAgent);
                    if (doc != null) {
                        // Displayed image
                        String imageUrl = getDisplayedImageUrl(doc).toLowerCase();
                        if (!imageUrl.isEmpty()) {
                            // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
                            if (imageUrl.contains("/509.gif"))
                                throw new LimitReachedException("E-hentai download points regenerate over time or can be bought on e-hentai if you're in a hurry");
                            img = ParseHelper.urlToImageFile(imageUrl, order++, pageUrls.size(), StatusContent.SAVED);
                            result.add(img);

                            // "Click here if the image fails loading" link
                            elements = doc.select("#loadfail");
                            if (!elements.isEmpty()) {
                                e = elements.first();
                                String arg = e.attr("onclick");
                                // Get the argument between 's
                                int quoteBegin = arg.indexOf('\'');
                                int quoteEnd = arg.indexOf('\'', quoteBegin + 1);
                                arg = arg.substring(quoteBegin + 1, quoteEnd);
                                // Get the query URL
                                if (pageUrl.contains("?")) pageUrl += "&";
                                else pageUrl += "?";
                                pageUrl += "nl=" + arg;
                                // Get the final URL
                                if (URLUtil.isValidUrl(pageUrl)) {
                                    downloadParams.put("backupUrl", pageUrl);
                                    String downloadParamsStr = JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS);
                                    img.setDownloadParams(downloadParamsStr);
                                }
                            }
                        }
                    }
                    progress.advance();
                }
            }
            progress.complete();

            // If the process has been halted manually, the result is incomplete and should not be returned as is
            if (processHalted) throw new PreparationInterruptedException();
            return result;
        } finally {
            EventBus.getDefault().unregister(this);
        }
    }

    @Nullable
    public Optional<ImageFile> parseBackupUrl(@NonNull String url, int order, int maxPages) throws Exception {
        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, "nw=1")); // nw=1 (always) avoids the Offensive Content popup (equivalent to clicking the "Never warn me again" link)
        Document doc = getOnlineDocument(url, headers, Site.EHENTAI.canKnowHentoidAgent());
        if (doc != null) {
            String imageUrl = getDisplayedImageUrl(doc).toLowerCase();
            // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
            if (imageUrl.contains("/509.gif"))
                throw new LimitReachedException("Bandwidth limit reached");
            if (!imageUrl.isEmpty())
                return Optional.of(ParseHelper.urlToImageFile(imageUrl, order, maxPages, StatusContent.SAVED));
        }
        return Optional.empty();
    }

    private void fetchPageUrls(@Nonnull Document doc, List<String> pageUrls) {
        Elements imageLinks = doc.select(".gdtm div a"); // Normal thumbs
        if (null == imageLinks || imageLinks.isEmpty())
            imageLinks = doc.select(".gdtl a"); // Large thumbs
        for (Element e : imageLinks) pageUrls.add(e.attr("href"));
    }

    private String getDisplayedImageUrl(@Nonnull Document doc) {
        Elements elements = doc.select("img#img");
        if (!elements.isEmpty()) {
            Element e = elements.first();
            return e.attr("src");
        }
        return "";
    }

    /**
     * Download event handler called by the event bus
     *
     * @param event Download event
     */
    @Subscribe
    public void onDownloadEvent(DownloadEvent event) {
        switch (event.eventType) {
            case DownloadEvent.EV_PAUSE:
            case DownloadEvent.EV_CANCEL:
            case DownloadEvent.EV_SKIP:
                processHalted = true;
                break;
            default:
                // Other events aren't handled here
        }
    }
}
