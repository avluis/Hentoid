package me.devsaki.hentoid.parsers.content;

import java.util.Map;

public class FakkuGalleryMetadata {
    public FakkuContent content;
    public Map<String, FakkuPage> pages;
    public String key_hash;
    public String key_data;


    public static class FakkuContent {
        public String content_name;
        public String content_url;
        public String content_description;
        public String content_language;
        public String content_pages;
    }

    public static class FakkuPage {
        public String page;
        public String image;
        public String thumb;
    }
}
