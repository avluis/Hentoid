package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpHelper;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class MusesContent implements ContentParser {
    @Selector(value = "head [rel=canonical]", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(value = ".top-menu-breadcrumb a")
    private List<Element> breadcrumbs;
    @Selector(value = ".gallery img", attr = "data-src", defValue = "")
    private List<String> thumbs;
    @Selector(value = ".gallery a", attr = "href", defValue = "")
    private List<String> thumbLinks;

    private static final List<String> nonLegitPublishers = new ArrayList<>();
    private static final List<String> publishersWithAuthors = new ArrayList<>();

    static {
        nonLegitPublishers.add("various authors");
        nonLegitPublishers.add("hentai and manga english");

        publishersWithAuthors.add("various authors");
        publishersWithAuthors.add("fakku comics");
        publishersWithAuthors.add("hentai and manga english");
        publishersWithAuthors.add("renderotica comics");
        publishersWithAuthors.add("tg comics");
        publishersWithAuthors.add("affect3d comics");
        publishersWithAuthors.add("johnpersons.com comics");
    }

    @Nullable
    public Content toContent(@Nonnull String url) {
        // Gallery pages are the only ones whose gallery links end with numbers
        // The others are album lists
        for (int i = 0; i < thumbLinks.size(); i++) {
            if (!thumbLinks.get(i).endsWith("/" + (i + 1)))
                return new Content().setStatus(StatusContent.IGNORED);
        }

        Content result = new Content();

        result.setSite(Site.MUSES);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty() || thumbs.isEmpty()) return result.setStatus(StatusContent.IGNORED);

        result.setUrl(theUrl.replace(Site.MUSES.getUrl(), ""));
        result.setCoverImageUrl(Site.MUSES.getUrl() + thumbs.get(0));

        // == Circle (publisher), Artist and Series
        AttributeMap attributes = new AttributeMap();

        if (breadcrumbs.size() > 1) {
            // Default : book title is the last breadcrumb
            String bookTitle = Helper.capitalizeString(breadcrumbs.get(breadcrumbs.size() - 1).text());

            if (breadcrumbs.size() > 2) {
                // Element 1 is always the publisher (using CIRCLE as publisher never appears on the Hentoid UI)
                String publisher = breadcrumbs.get(1).text().toLowerCase();
                if (!nonLegitPublishers.contains(publisher))
                    ParseHelper.parseAttribute(attributes, AttributeType.CIRCLE, breadcrumbs.get(1), false, Site.MUSES);

                if (breadcrumbs.size() > 3) {
                    // Element 2 is either the author or the series, depending on the publisher
                    AttributeType type = AttributeType.SERIE;
                    if (publishersWithAuthors.contains(publisher)) type = AttributeType.ARTIST;
                    ParseHelper.parseAttribute(attributes, type, breadcrumbs.get(2), false, Site.MUSES);
                    // Add series to book title if it isn't there already
                    if (AttributeType.SERIE == type) {
                        String series = breadcrumbs.get(2).text();
                        if (!bookTitle.toLowerCase().startsWith(series.toLowerCase()))
                            bookTitle = series + " - " + bookTitle;
                    }

                    if (breadcrumbs.size() > 4) {
                        // All that comes after element 2 contributes to the book title
                        boolean first = true;
                        StringBuilder bookTitleBuilder = new StringBuilder();
                        for (int i = 3; i < breadcrumbs.size() - 1; i++) {
                            if (first) first = false;
                            else bookTitleBuilder.append(" - ");
                            bookTitleBuilder.append(breadcrumbs.get(i).text());
                        }
                        bookTitle = bookTitleBuilder.toString();
                    }
                }
            }
            result.setTitle(bookTitle);
        }


        result.setQtyPages(thumbs.size()); // We infer there are as many thumbs as actual book pages on the gallery summary webpage

        String[] thumbParts;
        int index = 1;
        List<ImageFile> images = new ArrayList<>();
        for (String s : thumbs) {
            thumbParts = s.split("/");
            if (thumbParts.length > 3) {
                thumbParts[2] = "fl"; // Large dimensions; there's also a medium variant available (fm)
                String imgUrl = Site.MUSES.getUrl() + "/" + thumbParts[1] + "/" + thumbParts[2] + "/" + thumbParts[3];
                images.add(new ImageFile(index, imgUrl, StatusContent.SAVED)); // We infer actual book page images have the same format as their thumbs
                index++;
            }
        }
        result.addImageFiles(images);

        // Tags are not shown on the album page, but on the picture page (!)
        try {
            Document doc = HttpHelper.getOnlineDocument(Site.MUSES.getUrl() + thumbLinks.get(0));
            if (doc != null) {
                Elements elements = doc.select(".album-tags a[href*='/search/tag']");
                if (!elements.isEmpty())
                    ParseHelper.parseAttributes(attributes, AttributeType.TAG, elements, true, Site.MUSES);
            }
        } catch (IOException e) {
            Timber.e(e);
        }
        result.addAttributes(attributes);


        return result;
    }
}
