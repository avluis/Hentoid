package me.devsaki.hentoid.dirpicker.events;

import java.io.File;

/**
 * Created by avluis on 06/11/2016.
 * Directory Chosen Event
 */
public class OnDirChosenEvent {
    private final File dir;

    public OnDirChosenEvent(File file) {
        this.dir = file;
    }

    public File getDir() {
        return dir;
    }
}
