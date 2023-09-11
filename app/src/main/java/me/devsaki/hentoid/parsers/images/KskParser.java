package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.Settings;
import me.devsaki.hentoid.util.exception.ParseException;
import me.devsaki.hentoid.util.network.HttpHelper;

public class KskParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        // Fetch the book gallery page
        Document doc = getOnlineDocument(content.getGalleryUrl());
        if (null == doc)
            throw new ParseException("Document unreachable : " + content.getGalleryUrl());

        return parseImages(doc.select("#previews>main>div img"));
    }

    public static List<String> parseImages(@NonNull List<Element> thumbs) {
        String prefix = Settings.INSTANCE.isKskDownloadOriginal() ? "original" : "resampled";
        return Stream.of(thumbs)
                .map(ParseHelper::getImgSrc)
                .map(q -> HttpHelper.fixUrl(q, Site.KSK.getUrl()))
                .map(r -> r.replace("/t/", "/" + prefix + "/"))
                .map(r -> r.replace("/320/", "/"))
                .toList();
    }
}
