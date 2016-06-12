package me.devsaki.hentoid.dirpicker.events;

import java.io.File;

/**
 * Created by avluis on 06/11/2016.
 * Current Root Directory Changed Event
 */
public class CurrentRootDirChangedEvent {
    private final File currentDir;

    public CurrentRootDirChangedEvent(File currentDir) {
        this.currentDir = currentDir;
    }

    public File getCurrentDirectory() {
        return currentDir;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CurrentRootDirChangedEvent event = (CurrentRootDirChangedEvent) o;

        return currentDir != null ? currentDir.equals(event.currentDir) : event.currentDir == null;
    }

    @Override
    public int hashCode() {
        return currentDir != null ? currentDir.hashCode() : 0;
    }
}
