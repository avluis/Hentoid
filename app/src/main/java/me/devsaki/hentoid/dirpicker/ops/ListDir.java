package me.devsaki.hentoid.dirpicker.ops;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.dirpicker.events.OpFailedEvent;
import me.devsaki.hentoid.dirpicker.model.DirTree;
import me.devsaki.hentoid.dirpicker.model.FileBuilder;
import me.devsaki.hentoid.dirpicker.observers.ListDirObserver;
import timber.log.Timber;

/**
 * Created by avluis on 06/12/2016.
 * List Directory Operation
 */
class ListDir {

    private final DirTree dirTree;
    private final EventBus bus;
//    private Subscription subscription;

    ListDir(DirTree dirTree, EventBus bus) {
        this.dirTree = dirTree;
        this.bus = bus;
    }

    void process(File rootDir) {
        if (rootDir.canRead()) {
//            cancelPrevOp();
            updateDirList(rootDir);

            Observer<File> observer = new ListDirObserver(dirTree, bus);

            // TODO - anti-leak measures
            /*            subscription =*/
            Observable.fromArray(rootDir.listFiles())
                    .filter(File::isDirectory)
                    .subscribeOn(Schedulers.io())
//                    .onBackpressureBuffer()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(observer);
        } else {
            Timber.d("Failed to process directory list.");
            bus.post(new OpFailedEvent());
        }
    }

    /*
        private void cancelPrevOp() {
            if (subscription != null && !subscription.isUnsubscribed()) {
                subscription.unsubscribe();
            }
            subscription = null;
        }
    */
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
