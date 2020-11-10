package me.devsaki.hentoid.parsers.images;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

public class ExHentaiParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();

    private boolean processHalted = false;


    public List<ImageFile> parseImageList(@NonNull Content content) throws Exception {
        EventBus.getDefault().register(this);

        List<ImageFile> result = new ArrayList<>();
        boolean useHentoidAgent = Site.EXHENTAI.canKnowHentoidAgent();

        try {
            String downloadParamsStr = content.getDownloadParams();
            if (null == downloadParamsStr || downloadParamsStr.isEmpty()) {
                Timber.e("Download parameters not set");
                return result;
            }

            Map<String, String> downloadParams;
            try {
                downloadParams = JsonHelper.jsonToObject(downloadParamsStr, JsonHelper.MAP_STRINGS);
            } catch (IOException e) {
                Timber.e(e);
                return result;
            }

            if (!downloadParams.containsKey(HttpHelper.HEADER_COOKIE_KEY)) {
                Timber.e("Download parameters do not contain any cookie");
                return result;
            }

            List<Pair<String, String>> headers = new ArrayList<>();
            String cookieValue = downloadParams.get(HttpHelper.HEADER_COOKIE_KEY) + "; nw=1"; // nw=1 (always) avoids the Offensive Content popup (equivalent to clicking the "Never warn me again" link)
            headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieValue));

            /*
             * 1- Detect the number of pages of the gallery
             *
             * 2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
             *
             * 3- Open all pages and grab the URL of the displayed image
             */

            // 1- Detect the number of pages of the gallery
            Document doc = getOnlineDocument(content.getGalleryUrl(), headers, useHentoidAgent);
            if (doc != null) {
                Elements elements = doc.select("table.ptt a");
                if (null == elements || elements.isEmpty()) return result;

                int tabId = (1 == elements.size()) ? 0 : elements.size() - 2;
                int nbGalleryPages = Integer.parseInt(elements.get(tabId).text());

                progress.start(nbGalleryPages + content.getQtyPages());

                // 2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
                List<String> pageUrls = new ArrayList<>();

                EHentaiParser.fetchPageUrls(doc, pageUrls);

                if (nbGalleryPages > 1) {
                    for (int i = 1; i < nbGalleryPages && !processHalted; i++) {
                        doc = getOnlineDocument(content.getGalleryUrl() + "/?p=" + i, headers, useHentoidAgent);
                        if (doc != null) EHentaiParser.fetchPageUrls(doc, pageUrls);
                        progress.advance();
                    }
                }

                // 3- Open all pages and
                //    - grab the URL of the displayed image
                //    - grab the alternate URL of the "Click here if the image fails loading" link
                result.add(ImageFile.newCover(content.getCoverImageUrl(), StatusContent.SAVED));
                int order = 1;

                for (String pageUrl : pageUrls) {
                    if (processHalted) break;
                    ImageFile img = EHentaiParser.parsePage(pageUrl, headers, useHentoidAgent, order++, pageUrls.size());
                    if (img != null) result.add(img);
                    progress.advance();
                }

                if (result.isEmpty() && doc != null)
                    throw new EmptyResultException("urls:" + pageUrls.size() + ",page:" + Helper.encode64(doc.toString()));
            }
            progress.complete();

            // If the process has been halted manually, the result is incomplete and should not be returned as is
            if (processHalted) throw new PreparationInterruptedException();
        } finally {
            EventBus.getDefault().unregister(this);
        }
        return result;
    }

    @Nullable
    public Optional<ImageFile> parseBackupUrl(@NonNull String url, int order, int maxPages) throws Exception {
        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, "nw=1")); // nw=1 (always) avoids the Offensive Content popup (equivalent to clicking the "Never warn me again" link)
        Document doc = getOnlineDocument(url, headers, Site.EXHENTAI.canKnowHentoidAgent());
        if (doc != null) {
            String imageUrl = EHentaiParser.getDisplayedImageUrl(doc).toLowerCase();
            // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
            if (imageUrl.contains("/509.gif"))
                throw new LimitReachedException("Exhentai download points regenerate over time or can be bought on e-hentai if you're in a hurry");
            if (!imageUrl.isEmpty())
                return Optional.of(ParseHelper.urlToImageFile(imageUrl, order, maxPages, StatusContent.SAVED));
        }
        return Optional.empty();
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
