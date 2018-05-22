package me.devsaki.hentoid.parsers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.AttributeMap;
import timber.log.Timber;

public class PandaParser extends BaseParser {

    @Override
    protected Content parseContent(Document doc) {
        Content result = new Content();

        result.setUrl(doc.baseUri().substring(doc.baseUri().indexOf('/', 9)));

        String coverUrl = doc.select("div#imgholder")
                .select("a")
                .select("img")
                .attr("src");
        result.setCoverImageUrl(coverUrl);

        String title = doc.select("div#mangainfo")
                .select("div")
                .select("h1")
                .text();
        result.setTitle(title);

        String lastOptionUrl = doc.select("div#selectpage")
                .select("select")
                .select("option")
                .last()
                .attr("value");
        int nbPages = Integer.parseInt(lastOptionUrl.substring(lastOptionUrl.lastIndexOf('/') + 1));
        result.setQtyPages(nbPages);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        result.setSite(Site.PANDA);

        return result;
    }

    @Override
    protected List<String> parseImages(Content content) throws IOException {
        List<String> result = new ArrayList<>();

        String pageUrl;
        for (int i = 0; i < content.getQtyPages(); i++) {
            pageUrl = content.getReaderUrl() + "/" + i;
            Document doc = Jsoup.connect(pageUrl).get();

            Elements scripts = doc.head().select("script");
            for (Element e : scripts) {
                if (e.toString().contains("document['pu']")) // That's the one
                {
                    Pattern pattern = Pattern.compile("document\\['pu'\\] = '(.+)'");
                    Matcher matcher = pattern.matcher(e.toString());

                    if (matcher.find() && matcher.groupCount() > 0) {
                        result.add(matcher.group(1));
                    }
                    break;
                }
            }
        }

        return result;
    }
}
