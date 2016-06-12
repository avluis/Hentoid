package me.devsaki.hentoid.dirpicker.model;

import java.io.File;
import java.util.Comparator;

/**
 * Created by avluis on 06/12/2016.
 * File Comparator
 */
class FileComparator implements Comparator<File> {

    @Override
    public int compare(File f1, File f2) {
        return f1.getAbsolutePath().compareTo(f2.getAbsolutePath());
    }
}
