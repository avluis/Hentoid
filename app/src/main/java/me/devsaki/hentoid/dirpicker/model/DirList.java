package me.devsaki.hentoid.dirpicker.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by avluis on 06/12/2016.
 * Directory List Builder
 */
public class DirList extends ArrayList<File> {

    public void sort() {
        Collections.sort(this, new FileComparator());
    }
}
