package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.parsers.ParseHelper;

public class ASMHentaiParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws IOException {
        List<String> result = new ArrayList<>();

        // Fetch the reader page
        Document doc = getOnlineDocument(content.getReaderUrl());
        if (doc != null) {
            Elements imgContainer = doc.select("div.reader_overlay"); // New ASM layout

            if (imgContainer.isEmpty())
                imgContainer = doc.select("div.full_image"); // Old ASM layout
            if (imgContainer.isEmpty())
                imgContainer = doc.select("div.full_gallery"); // Older ASM layout
            Element imgElt = imgContainer.select("a").select("img").first();
            if (imgElt != null) {
                String imgUrl = ParseHelper.getImgSrc(imgElt);
                if (!imgUrl.startsWith("http")) imgUrl = "https:" + imgUrl;

                String ext = imgUrl.substring(imgUrl.lastIndexOf('.'));

                for (int i = 0; i < content.getQtyPages(); i++) {
                    String img = imgUrl.substring(0, imgUrl.lastIndexOf('/') + 1) + (i + 1) + ext;
                    result.add(img);
                }
            }
        }

        return result;
    }
}
