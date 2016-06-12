package me.devsaki.hentoid.dirpicker.events;

import java.io.File;

/**
 * Created by avluis on 06/11/2016.
 * Make (create) Directory Event
 */
public class OnMakeDirEvent {
    public final File root;
    public final String dirName;

    public OnMakeDirEvent(File root, String name) {
        this.root = root;
        this.dirName = name;
    }
}
