package me.devsaki.hentoid.json.sources;

import static me.devsaki.hentoid.parsers.ParseHelperKt.cleanup;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;

@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class EHentaiGalleriesMetadata {
    private List<EHentaiGalleryMetadata> gmetadata;

    public Content update(@NonNull Content content, @NonNull String url, @NonNull Site site, boolean updatePages) {
        return (gmetadata != null && !gmetadata.isEmpty()) ? gmetadata.get(0).update(content, url, site, updatePages) : new Content();
    }


    public static class EHentaiGalleryMetadata {

        private String gid;
        private String token;
        private String posted;
        private String title;
        private String category;
        private String thumb;
        private String filecount;
        private List<String> tags;


        public Content update(@NonNull Content content, @NonNull String url, @NonNull Site site, boolean updatePages) {
            AttributeMap attributes = new AttributeMap();

            content.setSite(site);

            content.setUrl("/" + gid + "/" + token); // The rest will not be useful anyway because of temporary keys
            content.setCoverImageUrl(thumb);
            content.setTitle(cleanup(title));
            content.setStatus(StatusContent.SAVED);

            if (category != null && !category.isBlank())
                attributes.add(new Attribute(AttributeType.CATEGORY, category.trim(), "category/" + category.trim(), site));

            if (posted != null && !posted.isEmpty())
                content.setUploadDate(Long.parseLong(posted) * 1000);

            if (updatePages) {
                if (filecount != null) content.setQtyPages(Integer.parseInt(filecount));
                else content.setQtyPages(0);
                content.setImageFiles(Collections.emptyList());
            }

            String[] tagParts;
            AttributeType type;
            String name;

            if (tags != null)
                for (String s : tags) {
                    tagParts = s.split(":");
                    if (1 == tagParts.length) {
                        type = AttributeType.TAG;
                        name = s;
                    } else {
                        name = tagParts[1];
                        switch (tagParts[0]) {
                            case "parody":
                                type = AttributeType.SERIE;
                                break;
                            case "character":
                                type = AttributeType.CHARACTER;
                                break;
                            case "language":
                                type = AttributeType.LANGUAGE;
                                break;
                            case "artist":
                                type = AttributeType.ARTIST;
                                break;
                            case "group":
                                type = AttributeType.CIRCLE;
                                break;
                            default:
                                type = AttributeType.TAG;
                                name = s;
                                break;
                        }
                    }

                    attributes.add(new Attribute(type, name, type.name() + "/" + name, site));
                }
            content.putAttributes(attributes);

            return content;
        }
    }

}
