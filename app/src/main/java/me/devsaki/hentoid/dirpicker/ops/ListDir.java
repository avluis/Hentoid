package me.devsaki.hentoid.dirpicker.ops;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.dirpicker.events.DataSetChangedEvent;
import me.devsaki.hentoid.dirpicker.events.OpFailedEvent;
import me.devsaki.hentoid.dirpicker.model.DirTree;
import me.devsaki.hentoid.dirpicker.model.FileBuilder;
import timber.log.Timber;

/**
 * Created by avluis on 06/12/2016.
 * List Directory Operation
 */
class ListDir {

    private final DirTree dirTree;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    ListDir(DirTree dirTree) {
        this.dirTree = dirTree;
    }

    void process(File rootDir) {
        if (rootDir.canRead()) {
            updateDirList(rootDir);

            dirTree.dirList.clear();

            compositeDisposable.add(
                    Observable.fromArray(rootDir.listFiles())
                            .filter(File::isDirectory)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(this::onNext, this::onError, this::onComplete)
            );
        } else {
            Timber.d("Failed to process directory list.");
            EventBus.getDefault().post(new OpFailedEvent());
        }
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

    private void onNext(File file) {
        dirTree.dirList.add(file);
    }

    private void onError(Throwable e) {
        Timber.d("onError: %s", e.toString());
        EventBus.getDefault().post(new OpFailedEvent());
    }

    private void onComplete() {
        dirTree.dirList.sort();

        if (dirTree.getParent() != null) {
            dirTree.dirList.add(0, dirTree.getParent());
        }
        EventBus.getDefault().post(new DataSetChangedEvent());
        Timber.d("Update directory list completed.");
    }

    void dispose() {
        compositeDisposable.clear();
    }
}
