package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.HbrowseParser;
import pl.droidsonroids.jspoon.annotation.Selector;

public class HbrowseContent extends BaseContentParser {
    @Selector("head script")
    private List<Element> scripts;
    @Selector("table.listTable tr")
    private List<Element> information;

    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.HBROWSE);
        if (url.isEmpty()) return content.setStatus(StatusContent.IGNORED);

        content.setUrl(url.replace(Site.HBROWSE.getUrl(), ""));

        content.populateUniqueSiteId();
        content.setCoverImageUrl(Site.HBROWSE.getUrl() + "/thumbnails/" + content.getUniqueSiteId() + "_1.jpg");

        if (null == information || information.isEmpty()) return content;

        AttributeMap attributes = new AttributeMap();

        for (Element e : information) {
            if (null == e.select("td strong").first()) continue;

            String metaType = e.select("td strong").first().childNode(0).toString();
            Element metaContent = e.select("td").last();

            if (metaType.equalsIgnoreCase("title"))
                content.setTitle(metaContent.childNode(0).toString());
            if (metaType.equalsIgnoreCase("artist"))
                addAttribute(metaContent, attributes, AttributeType.ARTIST);

            if (metaType.equalsIgnoreCase("type"))
                addAttribute(metaContent, attributes, AttributeType.TAG);
            if (metaType.equalsIgnoreCase("setting"))
                addAttribute(metaContent, attributes, AttributeType.TAG);
            if (metaType.equalsIgnoreCase("fetish"))
                addAttribute(metaContent, attributes, AttributeType.TAG);
            if (metaType.equalsIgnoreCase("relationship"))
                addAttribute(metaContent, attributes, AttributeType.TAG);
            if (metaType.equalsIgnoreCase("male body"))
                addAttribute(metaContent, attributes, AttributeType.TAG, "male");
            if (metaType.equalsIgnoreCase("female body"))
                addAttribute(metaContent, attributes, AttributeType.TAG, "female");
            if (metaType.equalsIgnoreCase("grouping"))
                addAttribute(metaContent, attributes, AttributeType.TAG);
            if (metaType.equalsIgnoreCase("scene"))
                addAttribute(metaContent, attributes, AttributeType.TAG);
            if (metaType.equalsIgnoreCase("position"))
                addAttribute(metaContent, attributes, AttributeType.TAG);
        }
        content.putAttributes(attributes);

        if (updateImages) {
            List<ImageFile> imgs = ParseHelper.urlsToImageFiles(HbrowseParser.parseImages(content, scripts), content.getCoverImageUrl(), StatusContent.SAVED);
            content.setImageFiles(imgs);
            content.setQtyPages(imgs.size() - 1);  // Don't count the cover
        }

        return content;
    }

    private void addAttribute(@NonNull final Element metaContent, @NonNull AttributeMap attributes, @NonNull AttributeType type) {
        addAttribute(metaContent, attributes, type, "");
    }

    private void addAttribute(@NonNull final Element metaContent, @NonNull AttributeMap attributes, @NonNull AttributeType type, @NonNull final String prefix) {
        if (!metaContent.children().isEmpty()) {
            List<Element> links = metaContent.select("a");
            if (!links.isEmpty())
                for (Element e : links)
                    ParseHelper.parseAttribute(e, attributes, type, Site.HBROWSE, prefix, false, null);
        } else
            attributes.add(new Attribute(type, prefix.isEmpty() ? "" : prefix + ":" + metaContent.childNode(0).toString(), metaContent.childNode(0).toString(), Site.HBROWSE));
    }
}
