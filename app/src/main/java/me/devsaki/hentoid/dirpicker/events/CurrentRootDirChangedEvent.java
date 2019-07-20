package me.devsaki.hentoid.dirpicker.events;

import java.io.File;
import java.util.Objects;

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
        if (!(o instanceof CurrentRootDirChangedEvent)) {
            return false;
        }

        CurrentRootDirChangedEvent event = (CurrentRootDirChangedEvent) o;

        return this == o || Objects.equals(event.currentDir, currentDir);
    }

    @Override
    public int hashCode() {
        return currentDir != null ? currentDir.hashCode() : 0;
    }
}
