package me.devsaki.hentoid.parsers.content;

import com.google.gson.annotations.Expose;

import java.util.HashMap;

public class FakkuGalleryMetadata {
    @Expose
    public FakkuContent content;
    @Expose
    public HashMap<String, FakkuPage> pages;
    @Expose
    public String key_hash;
    @Expose
    public String key_data;


    public class FakkuContent {
        @Expose
        public String content_name;
        @Expose
        public String content_url;
        @Expose
        public String content_description;
        @Expose
        public String content_language;
        @Expose
        public String content_pages;
    }

    public class FakkuPage {
        @Expose
        public String page;
        @Expose
        public String image;
        @Expose
        public String thumb;
    }
}
