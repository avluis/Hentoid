package me.devsaki.hentoid.dirpicker.observers;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import me.devsaki.hentoid.dirpicker.events.OpFailedEvent;
import me.devsaki.hentoid.dirpicker.events.UpdateDirTreeEvent;
import me.devsaki.hentoid.dirpicker.model.DirTree;
import me.devsaki.hentoid.util.LogHelper;
import rx.Observer;

/**
 * Created by avluis on 06/12/2016.
 * Make Directory Observer
 */
public class MakeDirObserver implements Observer<File> {
    private static final String TAG = LogHelper.makeLogTag(MakeDirObserver.class);

    private final DirTree dirTree;
    private final EventBus bus;
    private File newDir;

    public MakeDirObserver(DirTree dirTree, EventBus bus) {
        this.dirTree = dirTree;
        this.bus = bus;
    }

    private boolean isNewDirInCurrentDir() {
        if (newDir == null) {
            return false;
        }

        File rootDir = dirTree.getRoot();
        File parentDirOfNewDir = newDir.getParentFile();

        return rootDir.getAbsolutePath().equals(parentDirOfNewDir.getAbsolutePath());
    }

    @Override
    public void onCompleted() {
        if (isNewDirInCurrentDir()) {
            bus.post(new UpdateDirTreeEvent(dirTree.getRoot()));
        }
        LogHelper.d(TAG, "Make directory/update directory list completed.");
    }

    @Override
    public void onError(Throwable e) {
        LogHelper.d(TAG, "onError: " + e.toString());
        bus.post(new OpFailedEvent());
    }

    @Override
    public void onNext(File file) {
        newDir = file;
    }
}
