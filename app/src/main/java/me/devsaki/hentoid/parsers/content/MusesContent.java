package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class MusesContent extends BaseContentParser {
    @Selector(value = ".top-menu-breadcrumb a")
    private List<Element> breadcrumbs;
    @Selector(value = ".gallery a")
    private List<Element> thumbLinks;

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
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        // Gallery pages are the only ones whose gallery links end with numbers
        // The others are album lists
        int nbImages = 0;
        List<String> imagesUrls = new ArrayList<>();
        for (Element thumbLink : thumbLinks) {
            String href = thumbLink.attr("href");
            int numSeparator = href.lastIndexOf('/');
            if (StringHelper.isNumeric(href.substring(numSeparator + 1))) {
                Element img = thumbLink.select("img").first();
                if (null == img) continue;
                String src = ParseHelper.getImgSrc(img);
                if (src.isEmpty()) continue;
                imagesUrls.add(src);

                nbImages++;
            }
        }
        if (nbImages < thumbLinks.size() / 3) return new Content().setStatus(StatusContent.IGNORED);

        content.setSite(Site.MUSES);
        String theUrl = canonicalUrl.isEmpty() ? url : canonicalUrl;
        if (theUrl.isEmpty() || 0 == nbImages) return content.setStatus(StatusContent.IGNORED);

        content.setUrl(theUrl.replace(Site.MUSES.getUrl(), "").replace("https://comics.8muses.com", ""));

        // == Circle (publisher), Artist and Series
        AttributeMap attributes = new AttributeMap();

        if (breadcrumbs.size() > 1) {
            // Default : book title is the last breadcrumb
            String bookTitle = StringHelper.capitalizeString(breadcrumbs.get(breadcrumbs.size() - 1).text());

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
                        for (int i = 3; i < breadcrumbs.size(); i++) {
                            if (first) first = false;
                            else bookTitleBuilder.append(" - ");
                            bookTitleBuilder.append(breadcrumbs.get(i).text());
                        }
                        bookTitle = bookTitleBuilder.toString();
                    }
                }
            }
            content.setTitle(StringHelper.removeNonPrintableChars(bookTitle));
        }

        if (updateImages) {
            content.setQtyPages(nbImages); // Cover is duplicated in the code below; no need to decrease nbImages here

            String[] thumbParts;
            int index = 0;
            List<ImageFile> images = new ArrayList<>();
            // Cover
            ImageFile cover = ImageFile.fromImageUrl(index++, Site.MUSES.getUrl() + imagesUrls.get(0), StatusContent.SAVED, nbImages);
            content.setCoverImageUrl(cover.getUrl());
            cover.setIsCover(true);
            images.add(cover);
            // Images
            for (String u : imagesUrls) {
                thumbParts = u.split("/");
                if (thumbParts.length > 3) {
                    thumbParts[2] = "fl"; // Large dimensions; there's also a medium variant available (fm)
                    String imgUrl = Site.MUSES.getUrl() + "/" + thumbParts[1] + "/" + thumbParts[2] + "/" + thumbParts[3];
                    images.add(ImageFile.fromImageUrl(index++, imgUrl, StatusContent.SAVED, nbImages)); // We infer actual book page images have the same format as their thumbs
                }
            }
            content.setImageFiles(images);
        }

        // Tags are not shown on the album page, but on the picture page (!)
        try {
            Document doc = HttpHelper.getOnlineDocument(Site.MUSES.getUrl() + thumbLinks.get(thumbLinks.size() - 1).attr("href"));
            if (doc != null) {
                Elements elements = doc.select(".album-tags a[href*='/search/tag']");
                if (!elements.isEmpty())
                    ParseHelper.parseAttributes(attributes, AttributeType.TAG, elements, false, Site.MUSES);
            }
        } catch (IOException e) {
            Timber.e(e);
        }
        content.putAttributes(attributes);


        return content;
    }
}
