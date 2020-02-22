package me.devsaki.hentoid.json.sources;

import java.util.Collections;
import java.util.List;

public class HitomiGalleryInfo {

    private List<HitomiGalleryPage> files;

    public List<HitomiGalleryPage> getFiles() {
        if (null == files) return Collections.emptyList();
        else return files;
    }

    public static class HitomiGalleryPage {
        private String hash;
        private Integer haswebp;
        private String name;

        public String getHash() {
            return hash;
        }

        public Integer getHaswebp() {
            return haswebp;
        }

        public String getName() {
            return name;
        }
    }

}