package me.devsaki.hentoid.json.sources;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HitomiGalleryInfo {

    private List<HitomiGalleryPage> files;

    public List<HitomiGalleryPage> getFiles() {
        if (null == files) return Collections.emptyList();
            // Sort files by anything that resembles a number inside their names (Hitomi may order the pages wrongly)
        else return Stream.of(files).sorted(new HitomiPageNameComparator()).toList();
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
            return (null == name) ? "" : name;
        }
    }

    private static class HitomiPageNameComparator implements Comparator<HitomiGalleryPage> {
        @Override
        public int compare(@NonNull HitomiGalleryPage o1, @NonNull HitomiGalleryPage o2) {
            return CaseInsensitiveSimpleNaturalComparator.getInstance().compare(o1.getName(), o2.getName());
        }
    }
}