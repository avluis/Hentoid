package me.devsaki.hentoid.parsers.content;

import static me.devsaki.hentoid.parsers.images.Hentai2ReadParser.IMAGE_PATH;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.activities.sources.Hentai2ReadActivity;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.Hentai2ReadParser;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class Hentai2ReadContent extends BaseContentParser {

    private static final Pattern GALLERY_PATTERN = Pattern.compile(Hentai2ReadActivity.GALLERY_PATTERN);

    @Selector(value = "div.img-container img[src*=cover]")
    private Element cover;
    @Selector(value = "span[itemprop^=name]")
    private List<Element> title;
    @Selector("ul.list li")
    private List<Element> properties;
    @Selector(value = "li.dropdown a[data-mid]", attr = "data-mid", defValue = "")
    private String uniqueId;

    @Selector(value = "script")
    private List<Element> scripts;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.HENTAI2READ);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);
        content.setRawUrl(url);

        if (GALLERY_PATTERN.matcher(url).find()) return updateGallery(content, updateImages);
        else return updateSingleChapter(content, url, updateImages);
    }

    public Content updateSingleChapter(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        String[] urlParts = url.split("/");
        if (urlParts.length > 1)
            content.setUniqueSiteId(urlParts[urlParts.length - 2]);
        else
            content.setUniqueSiteId(urlParts[0]);

        try {
            Hentai2ReadParser.H2RInfo info = Hentai2ReadParser.getDataFromScripts(scripts);
            if (info != null) {
                String title = StringHelper.removeNonPrintableChars(info.title);
                content.setTitle(title);

                List<String> chapterImgs = Stream.of(info.images).map(s -> IMAGE_PATH + s).toList();
                if (updateImages && !chapterImgs.isEmpty()) {
                    String coverUrl = chapterImgs.get(0);
                    content.setImageFiles(ParseHelper.urlsToImageFiles(chapterImgs, coverUrl, StatusContent.SAVED));
                    content.setQtyPages(chapterImgs.size());
                }
            }
        } catch (IOException ioe) {
            Timber.w(ioe);
        }

        return content;
    }

    public Content updateGallery(@NonNull final Content content, boolean updateImages) {
        if (cover != null)
            content.setCoverImageUrl(ParseHelper.getImgSrc(cover));
        if (title != null && !title.isEmpty()) {
            String titleStr = title.get(title.size() - 1).text(); // Last span is the title
            content.setTitle(!titleStr.isEmpty() ? StringHelper.removeNonPrintableChars(titleStr) : "");
        } else content.setTitle(NO_TITLE);
        content.setUniqueSiteId(uniqueId);

        AttributeMap attributes = new AttributeMap();
        String currentProperty = "";
        if (properties != null)
            for (Element e : properties) {
                for (Element child : e.children()) {
                    if (child.nodeName().equals("b"))
                        currentProperty = child.text().toLowerCase().trim();
                    else if (child.nodeName().equals("a")) {
                        switch (currentProperty) {
                            /*
                            Apparently, we can't trust that figure as some books have less actual chapters/pages than advertised
                            case "page":
                                String qtyPagesStr = child.text().substring(0, child.text().indexOf(" page")).replace(",", "");
                                qtyPages = Integer.parseInt(qtyPagesStr);
                                break;
                             */
                            case "parody":
                                ParseHelper.parseAttribute(attributes, AttributeType.SERIE, child, false, Site.HENTAI2READ);
                                break;
                            case "artist":
                                ParseHelper.parseAttribute(attributes, AttributeType.ARTIST, child, false, Site.HENTAI2READ);
                                break;
                            case "language":
                                ParseHelper.parseAttribute(attributes, AttributeType.LANGUAGE, child, false, Site.HENTAI2READ);
                                break;
                            case "character":
                                ParseHelper.parseAttribute(attributes, AttributeType.CHARACTER, child, false, Site.HENTAI2READ);
                                break;
                            case "content":
                            case "category":
                                ParseHelper.parseAttribute(attributes, AttributeType.TAG, child, false, Site.HENTAI2READ);
                                break;
                            default:
                                // Other cases aren't interesting
                        }
                    }
                }
            }
        content.putAttributes(attributes);

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }
}
