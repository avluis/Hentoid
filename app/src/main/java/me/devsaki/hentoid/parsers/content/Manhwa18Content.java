package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.activities.sources.Manhwa18Activity;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class Manhwa18Content extends BaseContentParser {

    private static final Pattern GALLERY_PATTERN = Pattern.compile(Manhwa18Activity.GALLERY_PATTERN);

    @Selector(value = ".series-cover div div", attr = "style", defValue = "")
    private String cover;
    @Selector(value = ".series-name a")
    private Element title;
    @Selector(value = ".series-information a[href*=tac-gia]")
    private List<Element> artists;
    @Selector(value = ".series-information a[href*=genre]")
    private List<Element> tags;

    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private String chapterTitle;
    @Selector(value = "#chapter-content img")
    private List<Element> chapterImgs;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.MANHWA18);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);
        content.setUrl(url.replace(Site.MANHWA18.getUrl(), "").replace("/gallery", ""));

        if (GALLERY_PATTERN.matcher(url).find()) return updateGallery(content, url, updateImages);
        else return updateSingleChapter(content, url, updateImages);
    }

    public Content updateSingleChapter(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        String title = StringHelper.removeNonPrintableChars(chapterTitle);
        title = StringEscapeUtils.unescapeHtml4(title);
        content.setTitle(title);
        String[] urlParts = url.split("/");
        if (urlParts.length > 1)
            content.setUniqueSiteId(urlParts[urlParts.length - 2]);
        else
            content.setUniqueSiteId(urlParts[0]);

        if (updateImages) {
            List<String> imgUrls = Stream.of(chapterImgs).map(ParseHelper::getImgSrc).toList();
            String coverUrl = "";
            if (!imgUrls.isEmpty()) coverUrl = imgUrls.get(0);
            content.setImageFiles(ParseHelper.urlsToImageFiles(imgUrls, coverUrl, StatusContent.SAVED));
            content.setQtyPages(imgUrls.size());
        }

        return content;
    }

    public Content updateGallery(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        if (cover != null) {
            cover = cover.replace("background-image:", "")
                    .replace("url('", "")
                    .replace("')", "")
                    .replace(";", "")
                    .trim();
            content.setCoverImageUrl(cover);
        }

        String titleStr = NO_TITLE;
        if (title != null) {
            titleStr = StringHelper.removeNonPrintableChars(title.text());
            titleStr = ParseHelper.removeTextualTags(titleStr);
        }
        content.setTitle(titleStr);

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.MANHWA18);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, "badge", Site.MANHWA18);
        content.putAttributes(attributes);

        return content;
    }
}
