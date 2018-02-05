package me.devsaki.hentoid.dirpicker.ops;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import me.devsaki.hentoid.dirpicker.events.OpFailedEvent;
import me.devsaki.hentoid.dirpicker.model.DirTree;
import me.devsaki.hentoid.dirpicker.model.FileBuilder;
import me.devsaki.hentoid.dirpicker.observable.ListDirObservable;
import me.devsaki.hentoid.dirpicker.observers.ListDirObserver;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by avluis on 06/12/2016.
 * List Directory Operation
 */
class ListDir {

    private final DirTree dirTree;
    private final EventBus bus;
    private Subscription subscription;

    ListDir(DirTree dirTree, EventBus bus) {
        this.dirTree = dirTree;
        this.bus = bus;
    }

    void process(File rootDir) {
        if (rootDir.canRead()) {
            cancelPrevOp();
            updateDirList(rootDir);

            Observable<File> observable = new ListDirObservable().create(rootDir);
            Observer<File> observer = new ListDirObserver(dirTree, bus);

            subscription = observable.subscribeOn(Schedulers.io())
                    .onBackpressureBuffer()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(observer);
        } else {
            Timber.d("Failed to process directory list.");
            bus.post(new OpFailedEvent());
        }
    }

    private void cancelPrevOp() {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
        subscription = null;
    }

    private void updateDirList(File rootDir) {
        dirTree.setRootDir(rootDir);
        updateParentDir(rootDir);
    }

    private void updateParentDir(File rootDir) {
        File parentDir = rootDir.getParentFile();
        dirTree.setParentDir(parentDir);

        if (parentDir != null) {
            FileBuilder parent = new FileBuilder(parentDir.getPath());
            parent.setName("../");
            dirTree.setParentDir(parent);
        }
    }
}
