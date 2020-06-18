package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.Helper;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

public class NexusParser extends BaseParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws IOException {
        List<String> result = new ArrayList<>();

        progressStart(content.getQtyPages());
        /*
         * Open all pages and grab the URL of the displayed image
         */
        for (int i = 0; i < content.getQtyPages(); i++) {
            String readerUrl = content.getReaderUrl().replace("001", Helper.formatIntAsStr(i + 1, 3));
            Document doc = getOnlineDocument(readerUrl);
            if (doc != null) {
                Elements elements = doc.select("section a img");
                if (elements != null && !elements.isEmpty()) {
                    Element e = elements.first();
                    result.add(e.attr("src"));
                }
            }
            progressPlus();
        }

        progressComplete();

        return result;
    }
}
