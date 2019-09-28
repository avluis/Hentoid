package me.devsaki.hentoid.parsers.content;

import java.util.HashMap;

public class FakkuGalleryMetadata {
    public FakkuContent content;
    public HashMap<String, FakkuPage> pages;
    public String key_hash;
    public String key_data;


    public class FakkuContent {
        public String content_name;
        public String content_url;
        public String content_description;
        public String content_language;
        public String content_pages;
    }

    public class FakkuPage {
        public String page;
        public String image;
        public String thumb;
    }
}
