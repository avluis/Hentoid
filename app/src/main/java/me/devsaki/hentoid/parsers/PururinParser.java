package me.devsaki.hentoid.parsers;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.AttributeMap;

/**
 * Created by robb_w on 01/31/2018.
 * Handles parsing of content from pururin.io
 */
public class PururinParser extends BaseParser {

    private final static String IMAGE_PATH = "//pururin.io/assets/images/data/";

    private class PururinInfo
    {
        @Expose
        String image_extension;
        @Expose
        String id;
    }

    @Override
    protected Content parseContent(Document doc) {
        Content result = null;

        Elements content = doc.select("div.gallery-wrapper");

        if (content.size() > 0) {
            result = new Content();

            String url = doc.baseUri();
            String protocol = url.substring(0,5);
            if ("https".equals(protocol)) protocol = "https:";
            url = url.replace(protocol+"//pururin.io/gallery","");
            result.setUrl(url);

            String coverUrl = doc.select("div.cover-wrapper")
                    .select("img")
                    .attr("src");
            if (!coverUrl.startsWith("http")) coverUrl = protocol+coverUrl;
            result.setCoverImageUrl(coverUrl);

            String title = doc.select("div.title").first().text();
            result.setTitle(title);

            Elements rows = content.select("tr");
            AttributeMap attributes = new AttributeMap();
            result.setAttributes(attributes);

            int pages = 0;

            for (Element element : rows) {
                Element td = element.select("td").first();
                if (td.html().startsWith("Artist")) {
                    parseAttributes(attributes, AttributeType.ARTIST, element.select("a"));
                } else if (td.html().startsWith("Circle")) {
                    parseAttributes(attributes, AttributeType.CIRCLE, element.select("a"));
                } else if (td.html().startsWith("Parody")) {
                    parseAttributes(attributes, AttributeType.SERIE, element.select("a"));
                } else if (td.html().startsWith("Character")) {
                    parseAttributes(attributes, AttributeType.CHARACTER, element.select("a"));
                } else if (td.html().startsWith("Contents")) {
                    parseAttributes(attributes, AttributeType.TAG, element.select("a"));
                } else if (td.html().startsWith("Language")) {
                    parseAttributes(attributes, AttributeType.LANGUAGE, element.select("a"));
                } else if (td.html().startsWith("Category")) {
                    parseAttributes(attributes, AttributeType.CATEGORY, element.select("a"));
                } else if (td.html().startsWith("Pages")) {
                    String pagesStr = element.select("td").get(1).text(); // pages ( size M )
                    int bracketPos = pagesStr.lastIndexOf("(");
                    if (bracketPos > -1) pagesStr = pagesStr.substring(0, bracketPos).trim();
                    pages = Integer.parseInt(pagesStr);
                }
            }

            result.setQtyPages(pages)
                    .setSite(Site.PURURIN);
        }

        return result;
    }


    @Override
    protected List<String> parseImages(Content content) throws Exception {
        List<String> result = new ArrayList<>();
        String url = content.getReaderUrl();
        String protocol = url.substring(0,5);
        if ("https".equals(protocol)) protocol = "https:";

        // The whole algorithm is in app.js
        // 1- Get image extension from gallery data (JSON on HTML body)
        // 2- Generate image URL from  imagePath constant, gallery ID, page number and extension

        // 1- Get image extension from gallery data (JSON on HTML body)
        Document doc = Jsoup.connect(url).get();
        String json = doc.select("gallery-read").attr(":gallery");
        PururinInfo info = new Gson().fromJson(json, PururinInfo.class);

        // 2- Get imagePath from app.js => it is constant anyway, and app.js is 3 MB long => put it there as a const
        for (int i = 0; i < content.getQtyPages(); i++) {
            result.add(protocol+IMAGE_PATH+info.id+"/"+(i+1)+"."+info.image_extension);
        }

        return result;
    }
}
