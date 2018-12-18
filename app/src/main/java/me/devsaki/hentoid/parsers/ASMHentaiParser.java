package me.devsaki.hentoid.parsers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.AttributeMap;

public class ASMHentaiParser extends BaseParser {

    @Override
    protected Content parseContent(Document doc) {
        return new Content(); // Useless; handled directly by ASMHentaiServer
    }

    @Override
    protected List<String> parseImages(Content content) throws IOException {
        List<String> result = new ArrayList<>();

        Document doc = Jsoup.connect(content.getReaderUrl()).get();
        String imgUrl = "http:" +
                doc.select("div.full_gallery")
                        .select("a")
                        .select("img")
                        .attr("src");

        String ext = imgUrl.substring(imgUrl.lastIndexOf('.'));

        for (int i = 0; i < content.getQtyPages(); i++) {
            String img = imgUrl.substring(0, imgUrl.lastIndexOf('/') + 1) + (i + 1) + ext;
            result.add(img);
        }

        return result;
    }
}
