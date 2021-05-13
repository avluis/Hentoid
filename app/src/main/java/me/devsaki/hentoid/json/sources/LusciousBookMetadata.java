package me.devsaki.hentoid.json.sources;

import androidx.annotation.NonNull;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.StringHelper;

@SuppressWarnings("unused, MismatchedQueryAndUpdateOfCollection")
public class LusciousBookMetadata {
    private BookData data;

    private static class BookData {
        private BookInfoContainer album;
    }

    private static class BookInfoContainer {
        private AlbumInfo get;
    }

    private static class AlbumInfo {
        private String id;
        private String title;
        private String url;
        private Integer number_of_pictures;
        private CoverInfo cover;
        private LanguageInfo language;
        private List<TagInfo> tags;
    }

    private static class CoverInfo {
        private String url;
    }

    private static class LanguageInfo {
        private String title;
        private String url;
    }

    private static class TagInfo {
        private String text;
        private String url;
    }

    private static final String RELATIVE_URL_PREFIX = "https://luscious.net";

    public Content update(@NonNull Content content) {
        content.setSite(Site.LUSCIOUS);

        AlbumInfo info = data.album.get;
        if (null == info.url || null == info.title) return content.setStatus(StatusContent.IGNORED);

        content.setUrl(info.url);

        content.setTitle(StringHelper.removeNonPrintableChars(info.title));

//        result.setQtyPages(info.number_of_pictures);  <-- does not reflect the actual number of pictures reachable via the Luscious API / website
        content.setCoverImageUrl(info.cover.url);

        AttributeMap attributes = new AttributeMap();
        if (info.language != null) {
            String name = StringHelper.removeNonPrintableChars(info.language.title.replace(" Language", ""));
            Attribute attribute = new Attribute(AttributeType.LANGUAGE, name, RELATIVE_URL_PREFIX + info.language.url, Site.LUSCIOUS);
            attributes.add(attribute);
        }

        if (info.tags != null) for (TagInfo tag : info.tags) {
            String name = StringHelper.removeNonPrintableChars(tag.text);
            if (name.contains(":"))
                name = name.substring(name.indexOf(':') + 1).trim(); // Clean all tags starting with "Type :" (e.g. "Artist : someguy")
            AttributeType type = AttributeType.TAG;
            if (tag.url.startsWith("/tags/artist:")) type = AttributeType.ARTIST;
//            else if (tag.url.startsWith("/tags/parody:")) type = AttributeType.SERIE;  <-- duplicate with series
            else if (tag.url.startsWith("/tags/character:")) type = AttributeType.CHARACTER;
            else if (tag.url.startsWith("/tags/series:")) type = AttributeType.SERIE;
            else if (tag.url.startsWith("/tags/group:")) type = AttributeType.ARTIST;
            Attribute attribute = new Attribute(type, name, RELATIVE_URL_PREFIX + tag.url, Site.LUSCIOUS);
            attributes.add(attribute);
        }
        content.putAttributes(attributes);

        return content;
    }
}
