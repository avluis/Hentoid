package me.devsaki.hentoid.parsers;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.FileHelper;

/**
 * Created by Shiro on 1/5/2016.
 * Handles parsing of content from nhentai
 */
public class NhentaiParser extends BaseParser {

    @Override
    protected List<String> parseImages(Content content) throws IOException {
        List<String> result = new ArrayList<>();

        String[] coverParts = content.getCoverImageUrl().split("/");
        String mediaId = coverParts[coverParts.length - 2];
        String imageExt = FileHelper.getExtension(content.getCoverImageUrl());
        String serverUrl = "https://i.nhentai.net/galleries/" + mediaId + "/";

        for (int i = 0; i < content.getQtyPages(); i++) {
            result.add(serverUrl + (i + 1) + "." + imageExt); // We infer the whole book is stored on the same server and has the same image format as its cover (both are verified as of 2019/01/14)
        }
/*
        String imageUrl = "";
        String pageUrl = content.getReaderUrl() + "1/";
        Document doc = getOnlineDocument(pageUrl);
        if (doc != null) {
            Element image = doc.body().select("#image-container img").first();
            if (image != null) imageUrl = image.attr("src");
        }
        String imageUrlPrefix = imageUrl.substring(0, imageUrl.lastIndexOf("/") + 1);
        String imageExt = FileHelper.getExtension(imageUrl);

        for (int i = 0; i < content.getQtyPages(); i++) {
            result.add(imageUrlPrefix + (i + 1) + "." + imageExt); // We infer the whole book is stored on the same server
        }
*/

        return result;
    }
}
