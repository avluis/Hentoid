package me.devsaki.hentoid.json.sources;

import static me.devsaki.hentoid.parsers.ParseHelperKt.urlsToImageFiles;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.StringHelper;

@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class AnchiraGalleryMetadata {

    public static final String IMG_HOST = "https://kisakisexo.xyz";

    private int id;
    private String key;
    private long uploaded_at;
    private long updated_at;
    private String title;
    private int pages;
    private String hash;
    private List<AnchiraPage> data;
    private String hash_resampled;
    private List<AnchiraPage> data_resampled;
    private List<AnchiraTag> tags;

    private static class AnchiraPage {
        private String n;
    }

    private static class AnchiraTag {
        private String name;
        private int namespace;
    }

    private void addAttribute(@NonNull AttributeType attributeType, @NonNull String name, @NonNull String url, @NonNull AttributeMap map) {
        Attribute attribute = new Attribute(attributeType, name, url, Site.ANCHIRA);
        map.add(attribute);
    }

    public Content toContent() {
        String url = Site.ANCHIRA.getUrl() + "/g/" + id + "/" + key;
        Content content = new Content();
        content.setSite(Site.ANCHIRA);
        content.setRawUrl(url);
        updateContent(content);
        return content;
    }

    public void updateContent(@NonNull Content content) {
        content.setTitle(StringHelper.removeNonPrintableChars(title));

        long uploadDate = uploaded_at;
        if (0 == uploadDate) uploadDate = updated_at;
        content.setUploadDate(uploadDate);

        AttributeMap attributes = new AttributeMap();
        for (AnchiraTag tag : tags) {
            if (0 == tag.namespace) addAttribute(AttributeType.TAG, tag.name, "", attributes);
            else if (1 == tag.namespace)
                addAttribute(AttributeType.ARTIST, tag.name, "", attributes);
        }
        content.putAttributes(attributes);

        content.setQtyPages(pages);

        if (!data.isEmpty()) {
            List<String> pageUrls = new ArrayList<>();
            for (AnchiraPage page : data) {
                pageUrls.add(IMG_HOST + "/" + id + "/" + key + "/" + hash + "/b/" + page.n);
            }
            String coverUrl = IMG_HOST + "/" + id + "/" + key + "/s/" + data.get(0).n;
            content.setImageFiles(urlsToImageFiles(pageUrls, coverUrl, StatusContent.SAVED));
            content.setCoverImageUrl(coverUrl);
        }
    }
}