package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.activities.sources.EdoujinActivity;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.EdoujinParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class EdoujinContent extends BaseContentParser {

    private static final Pattern GALLERY_PATTERN = Pattern.compile(EdoujinActivity.GALLERY_PATTERN);

    @Selector(value = ".thumb img")
    private Element cover;
    @Selector(value = ".entry-title")
    private Element title;
    @Selector(".infox .fmed")
    private List<Element> artist;
    @Selector(".mgen a")
    private List<Element> properties;
    @Selector(value = "time[itemprop='datePublished']", attr = "datetime")
    private String datePosted;
    @Selector(value = "time[itemprop='dateModified']", attr = "datetime")
    private String dateModified;

    @Selector(value = "script")
    private List<Element> scripts;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.EDOUJIN);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);
        content.setRawUrl(url);

        if (GALLERY_PATTERN.matcher(url).find()) return updateGallery(content, url, updateImages);
        else return updateSingleChapter(content, url, updateImages);
    }

    public Content updateSingleChapter(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        String[] urlParts = url.split("/");
        if (urlParts.length > 1) {
            urlParts = urlParts[urlParts.length - 1].split("-");
            if (urlParts.length > 1) content.setUniqueSiteId(urlParts[urlParts.length - 1]);
        }

        if (title != null) {
            String titleStr = title.text();
            content.setTitle(!titleStr.isEmpty() ? StringHelper.removeNonPrintableChars(titleStr) : "");
        } else content.setTitle(NO_TITLE);

        try {
            EdoujinParser.EdoujinInfo info = EdoujinParser.getDataFromScripts(scripts);
            if (info != null) {
                List<String> chapterImgs = info.getImages();
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

    public Content updateGallery(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        if (cover != null)
            content.setCoverImageUrl(ParseHelper.getImgSrc(cover));
        if (title != null) {
            String titleStr = title.text();
            content.setTitle(!titleStr.isEmpty() ? StringHelper.removeNonPrintableChars(titleStr) : "");
        } else content.setTitle(NO_TITLE);

        String[] urlParts = url.split("/");
        if (urlParts.length > 1) content.setUniqueSiteId(urlParts[urlParts.length - 1]);

        if (!dateModified.isEmpty())
            content.setUploadDate(Helper.parseDatetimeToEpoch(dateModified, "yyyy-MM-dd'T'HH:mm:ssXXX")); // e.g. 2022-02-02T02:44:17+07:00
        else if (!datePosted.isEmpty())
            content.setUploadDate(Helper.parseDatetimeToEpoch(datePosted, "yyyy-MM-dd'T'HH:mm:ssXXX")); // e.g. 2022-02-02T02:44:17+07:00

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, properties, false, Site.EDOUJIN);
        String currentProperty = "";
        if (artist != null)
            for (Element e : artist) {
                for (Element child : e.children()) {
                    if (child.nodeName().equals("b"))
                        currentProperty = child.text().toLowerCase().trim();
                    else if (child.nodeName().equals("span")) {
                        switch (currentProperty) {
                            case "artist":
                            case "author":
                                String data = StringHelper.removeNonPrintableChars(child.text().toLowerCase().trim());
                                if (data.length() > 1)
                                    attributes.add(new Attribute(AttributeType.ARTIST, data, "", Site.EDOUJIN));
                                break;
                            default:
                                // Nothing interesting there
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
