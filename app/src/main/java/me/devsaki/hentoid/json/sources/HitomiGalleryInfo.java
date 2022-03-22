package me.devsaki.hentoid.json.sources;

import androidx.annotation.NonNull;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.StringHelper;

@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class HitomiGalleryInfo {

    private List<HitomiParody> parodys;
    private List<HitomiTag> tags;
    private String title;
    private List<HitomiCharacter> characters;
    private List<HitomiGroup> groups;
    private String date; // Format : "YYYY-MM-DD HH:MM:SS-05" (-05 being the timezone of the server ?)
    private String language;
    private String language_localname;
    private String language_url;
    private List<HitomiArtist> artists;
    private String type;

    private static class HitomiParody {
        private String parody;
        private String url;
    }

    private static class HitomiTag {
        private String url;
        private String tag;
        private String female;
        private String male;

        String getLabel() {
            String result = StringHelper.protect(tag);
            if (female != null && female.equals("1")) result += " ♀";
            else if (male != null && male.equals("1")) result += " ♂";
            return result;
        }
    }

    private static class HitomiCharacter {
        private String url;
        private String character;
    }

    private static class HitomiGroup {
        private String url;
        private String group;
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
        content.setTitle(StringHelper.removeNonPrintableChars(title));

        content.setUploadDate(Helper.parseDatetimeToEpoch(date, "yyyy-MM-dd HH:mm:ssx"));

        AttributeMap attributes = new AttributeMap();
        if (parodys != null)
            for (HitomiParody parody : parodys)
                addAttribute(AttributeType.SERIE, parody.parody, parody.url, attributes);
        if (tags != null)
            for (HitomiTag tag : tags)
                addAttribute(AttributeType.TAG, tag.getLabel(), tag.url, attributes);
        if (characters != null)
            for (HitomiCharacter chara : characters)
                addAttribute(AttributeType.CHARACTER, chara.character, chara.url, attributes);
        if (groups != null)
            for (HitomiGroup group : groups)
                addAttribute(AttributeType.CIRCLE, group.group, group.url, attributes);
        if (artists != null)
            for (HitomiArtist artist : artists)
                addAttribute(AttributeType.ARTIST, artist.artist, artist.url, attributes);
        if (language != null)
            addAttribute(AttributeType.LANGUAGE, language, language_url, attributes);
        if (type != null)
            addAttribute(AttributeType.CATEGORY, type, "", attributes);
        content.putAttributes(attributes);
    }
}