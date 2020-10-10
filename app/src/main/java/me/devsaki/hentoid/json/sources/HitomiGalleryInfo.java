package me.devsaki.hentoid.json.sources;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.devsaki.hentoid.util.Helper;

public class HitomiGalleryInfo {

    private List<HitomiGalleryPage> files;

    public List<HitomiGalleryPage> getFiles() {
        if (null == files) return Collections.emptyList();
            // Sort files by anything that resembles a number inside their names (Hitomi may order the pages wrongly)
        else return Stream.of(files).sorted(new InnerNameNumberComparator()).toList();
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

    private static class InnerNameNumberComparator implements Comparator<HitomiGalleryPage> {
        @Override
        public int compare(@NonNull HitomiGalleryPage o1, @NonNull HitomiGalleryPage o2) {
            String name1 = o1.getName();
            if (null == name1) name1 = "";
            String name2 = o2.getName();
            if (null == name2) name2 = "";
            int innerNumber1 = Helper.extractNumeric(name1);
            if (-1 == innerNumber1) return name1.compareTo(name2);
            int innerNumber2 = Helper.extractNumeric(name2);
            if (-1 == innerNumber2) return name1.compareTo(name2);

            return Integer.compare(innerNumber1, innerNumber2);
        }
    }
}