package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.network.HttpHelper;

/**
 * Handles parsing of content from pururin.to
 */
public class PururinParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();

        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

        Document doc = getOnlineDocument(content.getGalleryUrl(), headers, Site.PURURIN.useHentoidAgent(), Site.PURURIN.useWebviewAgent());
        if (doc != null) {
            // Get all thumb URLs and convert them to page URLs
            List<String> imgSrc = Stream.of(doc.select(".gallery-preview img"))
                    .map(ParseHelper::getImgSrc)
                    .map(PururinParser::thumbToPage)
                    .toList();

            result.addAll(imgSrc);
        }

        return result;
    }

    private static String thumbToPage(String thumbUrl) {
        HttpHelper.UriParts parts = new HttpHelper.UriParts(thumbUrl);
        String name = parts.getFileNameNoExt();
        parts.setFileNameNoExt(name.substring(0, name.length() - 1)); // Remove the trailing 't'
        return parts.toUri();
    }
}
