package me.devsaki.hentoid.json.sources;

import androidx.annotation.NonNull;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;

@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class HitomiGalleryInfo {

    private List<HitomiParody> parodys;
    private List<HitomiTag> tags;
    private String title;
    private List<HitomiCharacter> characters;
    private List<HitomiGroup> groups;
    //    private Date date; TODO
    private List<HitomiLanguage> languages;
    private List<HitomiArtist> artists;
    private String type;

    private static class HitomiParody {
        private String parody;
        private String url;
    }

    private static class HitomiTag {
        private String url;
        private String tag;
    }

    private static class HitomiCharacter {
        private String url;
        private String character;
    }

    private static class HitomiGroup {
        private String url;
        private String group;
    }

    private static class HitomiLanguage {
        private String url;
        private String language;
    }

    private static class HitomiArtist {
        private String url;
        private String artist;
    }

    private void addAttribute(@NonNull AttributeType attributeType, @NonNull String name, @NonNull String url, @NonNull AttributeMap map) {
        Attribute attribute = new Attribute(attributeType, name, url, Site.HITOMI);
        map.add(attribute);
    }

    public void updateContent(@NonNull Content content) {
        content.setTitle(title);
//        content.setUploadDate(date.getTime()); TODO

        AttributeMap attributes = new AttributeMap();
        if (parodys != null)
            for (HitomiParody parody : parodys)
                addAttribute(AttributeType.SERIE, parody.parody, parody.url, attributes);
        if (tags != null)
            for (HitomiTag tag : tags)
                addAttribute(AttributeType.TAG, tag.tag, tag.url, attributes);
        if (characters != null)
            for (HitomiCharacter chara : characters)
                addAttribute(AttributeType.CHARACTER, chara.character, chara.url, attributes);
        if (groups != null)
            for (HitomiGroup group : groups)
                addAttribute(AttributeType.CIRCLE, group.group, group.url, attributes);
        if (languages != null)
            for (HitomiLanguage language : languages)
                addAttribute(AttributeType.LANGUAGE, language.language, language.url, attributes);
        if (artists != null)
            for (HitomiArtist artist : artists)
                addAttribute(AttributeType.ARTIST, artist.artist, artist.url, attributes);
        addAttribute(AttributeType.CATEGORY, type, "", attributes);
        content.putAttributes(attributes);
    }
}