package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.exception.ParseException;

public class NhentaiParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        // Fetch the book gallery page
        Document doc = getOnlineDocument(content.getGalleryUrl());
        if (null == doc)
            throw new ParseException("Document unreachable : " + content.getGalleryUrl());

        List<Element> thumbs = doc.select("#thumbnail-container img[data-src]");

        return parseImages(content, thumbs);
    }

    public static List<String> parseImages(@NonNull Content content, @NonNull List<Element> thumbs) {
        String[] coverParts = content.getCoverImageUrl().split("/");
        String mediaId = coverParts[coverParts.length - 2];
        String serverUrl = "https://i.nhentai.net/galleries/" + mediaId + "/"; // We infer the whole book is stored on the same server

        List<String> result = new ArrayList<>();

        int index = 1;
        for (Element e : thumbs) {
            String s = ParseHelper.getImgSrc(e);
            if (s.isEmpty()) continue;
            result.add(serverUrl + index++ + "." + FileHelper.getExtension(s));
        }

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
