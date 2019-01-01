package me.devsaki.hentoid.parsers;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devsaki.hentoid.database.domains.Content;

public class PandaParser extends BaseParser {

    @Override
    protected List<String> parseImages(Content content) throws IOException {
        List<String> result = new ArrayList<>();

        String pageUrl;
        for (int i = 0; i < content.getQtyPages(); i++) {
            pageUrl = content.getReaderUrl() + "/" + i;
            Document doc = getOnlineDocument(pageUrl);
            if (doc != null) {
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
        }

        return result;
    }
}
