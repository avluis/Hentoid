package me.devsaki.hentoid.dirpicker.events;

import java.io.File;

/**
 * Created by avluis on 06/11/2016.
 * Update Directory Tree Event
 */
public class UpdateDirTreeEvent {
    public final File rootDir;

    public UpdateDirTreeEvent(File rootDir) {
        this.rootDir = rootDir;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UpdateDirTreeEvent event = (UpdateDirTreeEvent) o;

        return rootDir != null ? rootDir.equals(event.rootDir) : event.rootDir == null;
    }

    @Override
    public int hashCode() {
        return rootDir != null ? rootDir.hashCode() : 0;
    }
}
