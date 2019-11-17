package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.LusciousGalleryMetadata;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class LusciousContent implements ContentParser {
    @Selector("#root h3 a")
    private String title;
    @Selector(value = "a[href^='/tags/artist:']")
    private List<Element> artists;
    @Selector(value = ".o-tag--inline a[href^='/tags/']")
    private List<Element> tags;
    @Selector(value = "a[href^='/genres/']")
    // Luscious differentiates between genres and tags but that's the same thing to Hentoid
    private List<Element> genres;
    @Selector(value = "a[href^='/languages/']")
    private List<Element> languages;
    @Selector(value = "body>script")
    private List<Element> scripts;


    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.LUSCIOUS);
        if (url.isEmpty() || null == scripts || scripts.isEmpty())
            return result.setStatus(StatusContent.IGNORED);

        result.setUrl(url.replace(Site.LUSCIOUS.getUrl(), ""));
        result.setTitle(Helper.removeNonPrintableChars(title));

        // Images
        List<ImageFile> images = getImages();
        result.setImageFiles(images);
        result.setQtyPages(images.size());

        result.setCoverImageUrl(images.get(0).getUrl()); // NB : getting its thumb instead would have been more proper

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true, Site.LUSCIOUS);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.LUSCIOUS);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, genres, true, Site.LUSCIOUS);
        // Languages (specific code because the layout is unusual)
        if (languages != null) for (Element element : languages) {
            if (element.childNodeSize() > 1) {
                String name = Helper.removeNonPrintableChars(element.child(1).text().replace(" Language", ""));
                Attribute attribute = new Attribute(AttributeType.LANGUAGE, name, element.attr("href"), Site.LUSCIOUS);
                attributes.add(attribute);
            }
        }
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, true, Site.LUSCIOUS);
        result.addAttributes(attributes);

        return result;
    }

    private List<ImageFile> getImages() {
        List<ImageFile> result = new ArrayList<>();

        // Detect and cleanup the JSON object describing album properties
        for (Element e : scripts) {
            String scriptContent = e.childNode(0).toString();
            if (scriptContent.contains("ANANSI_CACHE")) {
                // Get the whole JSON object
                int beginIndex = scriptContent.indexOf("ANANSI_CACHE");
                int endIndex = scriptContent.indexOf("}}}\";");
                String jsonObject = scriptContent.substring(beginIndex + 18, endIndex + 3);
                // Cleanup global escaping
                jsonObject = jsonObject.replace("\\\\", "\\").replace("\\r", "").replace("\\n", "").replace("\\\"", "\"");
                // Cleanup non-standard node attributes
                beginIndex = jsonObject.indexOf("AlbumListOwnPictures");
                endIndex = jsonObject.indexOf("}\":", beginIndex);
                jsonObject = jsonObject.substring(0, beginIndex + 20) + jsonObject.substring(endIndex + 1);
                try {
                    LusciousGalleryMetadata galleryMetadata = JsonHelper.jsonToObject(jsonObject, LusciousGalleryMetadata.class);
                    result = galleryMetadata.toImageFileList();
                } catch (IOException ex) {
                    Timber.e(ex);
                }
                break;
            }
        }

        return result;
    }
}
