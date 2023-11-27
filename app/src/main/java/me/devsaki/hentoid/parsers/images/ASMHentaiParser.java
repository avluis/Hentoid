package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.exception.ParseException;

public class ASMHentaiParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();

        // Fetch the reader page
        Document doc = getOnlineDocument(content.getReaderUrl());
        if (doc != null) {
            int nbPages = -1;
            Element nbPagesE = doc.select(".pages_btn").first();
            if (nbPagesE != null) {
                List<ImmutableTriple<Integer, Integer, Integer>> digits = StringHelper.locateDigits(nbPagesE.text());
                if (!digits.isEmpty()) nbPages = digits.get(digits.size() - 1).right;
            }
            if (-1 == nbPages) throw new ParseException("Couldn't find the number of pages");

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

                for (int i = 0; i < nbPages; i++) {
                    String img = imgUrl.substring(0, imgUrl.lastIndexOf('/') + 1) + (i + 1) + ext;
                    result.add(img);
                }
            }
        }

        return result;
    }

    @Override
    protected List<String> parseImages(@NonNull String chapterUrl, String downloadParams, List<Pair<String, String>> headers) throws Exception {
        // Nothing as ASM doesn't have chapters
        return null;
    }

    @Override
    protected boolean isChapterUrl(@NonNull String url) {
        return false;
    }
}
