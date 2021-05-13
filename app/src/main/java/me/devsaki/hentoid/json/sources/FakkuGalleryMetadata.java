package me.devsaki.hentoid.json.sources;

import java.util.Map;

@SuppressWarnings("unused, MismatchedQueryAndUpdateOfCollection")
public class FakkuGalleryMetadata {
    private FakkuContent content;
    private Map<String, FakkuPage> pages;
    private String key_hash;
    private String key_data;

    public Map<String, FakkuPage> getPages() {
        return pages;
    }

    public FakkuContent getContent() {
        return content;
    }

    public String getKeyHash() {
        return key_hash;
    }

    public String getKeyData() {
        return key_data;
    }

    private static class FakkuContent {
        private String content_name;
        private String content_url;
        private String content_description;
        private String content_language;
        private String content_pages;
    }

    public static class FakkuPage {
        private String page;
        private String image;
        private String thumb;

        public String getImage() {
            return image;
        }
    }
}
