package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.exception.ParseException;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

public class HbrowseParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        // Fetch the book gallery page
        Document doc = getOnlineDocument(content.getGalleryUrl());
        if (null == doc)
            throw new ParseException("Document unreachable : " + content.getGalleryUrl());

        List<Element> scripts = doc.select("head script");

        return parseImages(content, scripts);
    }

    public static List<String> parseImages(@NonNull Content content, @NonNull List<Element> scripts) {
        content.populateUniqueSiteId();
        List<String> result = new ArrayList<>();

        String chapter = "";
        String[] parts = content.getUrl().split("/");
        if (parts.length > 1) chapter = parts[1];

        for (Element e : scripts) {
            String scriptContent = e.toString();
            if (scriptContent.contains("list")) {
                int beginIndex = scriptContent.indexOf("list = [") + 8;
                String[] list = scriptContent.substring(beginIndex, scriptContent.indexOf("];", beginIndex)).replace("\"", "").split(",");
                for (String s : list) {
                    if (!s.trim().isEmpty() && !s.equalsIgnoreCase("zzz")) {
                        String imgUrl = Site.HBROWSE.getUrl() + "data/" + content.getUniqueSiteId() + "/" + chapter + "/" + s;
                        result.add(imgUrl);
                    }
                }
                break;
            }
        }

        return result;
    }
}
