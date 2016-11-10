package me.devsaki.hentoid.dirpicker.observers;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import me.devsaki.hentoid.dirpicker.events.DataSetChangedEvent;
import me.devsaki.hentoid.dirpicker.events.OpFailedEvent;
import me.devsaki.hentoid.dirpicker.model.DirTree;
import rx.Observer;
import timber.log.Timber;

/**
 * Created by avluis on 06/12/2016.
 * List Directory Observer
 */
public class ListDirObserver implements Observer<File> {

    private final DirTree dirTree;
    private final EventBus bus;

    public ListDirObserver(DirTree dirTree, EventBus bus) {
        this.dirTree = dirTree;
        this.bus = bus;

        dirTree.dirList.clear();
    }

    @Override
    public void onCompleted() {
        dirTree.dirList.sort();

        if (dirTree.getParent() != null) {
            dirTree.dirList.add(0, dirTree.getParent());
        }
        bus.post(new DataSetChangedEvent());
        Timber.d("Update directory list completed.");
    }

    @Override
    public void onError(Throwable e) {
        Timber.d("onError: %s", e.toString());
        bus.post(new OpFailedEvent());
    }

    @Override
    public void onNext(File file) {
        dirTree.dirList.add(file);
    }
}
