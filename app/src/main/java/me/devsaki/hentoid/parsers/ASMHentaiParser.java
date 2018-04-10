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
        Content result = new Content();

        Elements content = doc.select("div.info");
        if (content.size() > 0) {
            String url = doc.select("div.cover")
                    .select("a")
                    .attr("href")
                    .replace("/gallery", "");
            url = url.substring(0, url.length() - 2);
            result.setUrl(url);

            String coverUrl = "http:"
                    + doc.select("div.cover")
                    .select("a")
                    .select("img")
                    .attr("src");
            result.setCoverImageUrl(coverUrl);

            String title = doc.select("div.info")
                    .select("h1")
                    .first()
                    .text();
            result.setTitle(title);

            int pages = Integer.parseInt(doc.select("div.pages")
                    .get(0)
                    .select("h3")
                    .text()
                    .replace("Pages: ", ""));
            result.setQtyPages(pages);

            AttributeMap attributes = new AttributeMap();
            result.setAttributes(attributes);

            Elements artistElements = content
                    .select("div.tags:contains(Artists)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.ARTIST, artistElements, true);

            Elements tagElements = content
                    .select("div.tags:contains(Tags)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.TAG, tagElements, true);

            Elements seriesElements = content
                    .select("div.tags:contains(Parody)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.SERIE, seriesElements, true);

            Elements characterElements = content
                    .select("div.tags:contains(Characters)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.CHARACTER, characterElements, true);

            if (doc.baseUri().contains("comics"))
            {
                result.setSite(Site.ASMHENTAI_COMICS);
            } else {
                result.setSite(Site.ASMHENTAI);
            }

        }

        return result;
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
        // TODO: Verify extension types on this source
        String ext = imgUrl.substring(imgUrl.length() - 4);

        for (int i = 0; i < content.getQtyPages(); i++) {
            String img = imgUrl.substring(0, imgUrl.length() - 4) + (i + 1) + ext;
            result.add(img);
        }

        return result;
    }
}
