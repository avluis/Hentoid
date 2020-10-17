package me.devsaki.hentoid.json.sources;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.devsaki.hentoid.util.FileHelper;
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
            return (null == name) ? "" : name;
        }
    }

    private static class InnerNameNumberComparator implements Comparator<HitomiGalleryPage> {
        @Override
        public int compare(@NonNull HitomiGalleryPage o1, @NonNull HitomiGalleryPage o2) {
            String name1 = o1.getName();
            String name2 = o2.getName();
            // Compare only when the entire file name is numerical (see follow-up comments on #640)
            if (Helper.isNumeric(FileHelper.getFileNameWithoutExtension(name1)) && Helper.isNumeric(FileHelper.getFileNameWithoutExtension(name2)))
                return Long.compare(Helper.extractNumeric(name1), Helper.extractNumeric(name2));
            else
                return name1.compareTo(name2);
        }
    }
}