package me.devsaki.hentoid.dirpicker.model;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import me.devsaki.hentoid.dirpicker.events.CurrentRootDirChangedEvent;

/**
 * Created by avluis on 06/12/2016.
 * Directory Tree Builder
 */
public class DirTree {
    public final DirList dirList;
    private File root;
    private File parent;

    public DirTree() {
        dirList = new DirList();
    }

    public void setRootDir(File rootDir) {
        this.root = rootDir;
        EventBus.getDefault().post(new CurrentRootDirChangedEvent(rootDir));
    }

    public void setParentDir(File parentDir) {
        this.parent = parentDir;
    }

    public File getRoot() {
        return root;
    }

    public File getParent() {
        return parent;
    }
}
