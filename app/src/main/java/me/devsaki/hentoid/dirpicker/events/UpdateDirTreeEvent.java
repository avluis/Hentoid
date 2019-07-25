package me.devsaki.hentoid.dirpicker.events;

import java.io.File;
import java.util.Objects;

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
        if (!(o instanceof UpdateDirTreeEvent)) {
            return false;
        }

        UpdateDirTreeEvent event = (UpdateDirTreeEvent) o;

        return this == o || Objects.equals(event.rootDir, rootDir);
    }

    @Override
    public int hashCode() {
        return rootDir != null ? rootDir.hashCode() : 0;
    }
}
