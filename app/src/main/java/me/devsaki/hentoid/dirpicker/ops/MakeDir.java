package me.devsaki.hentoid.dirpicker.ops;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.dirpicker.model.DirTree;
import me.devsaki.hentoid.dirpicker.observable.MakeDirObservable;
import me.devsaki.hentoid.dirpicker.observers.MakeDirObserver;

/**
 * Created by avluis on 06/12/2016.
 * Make Directory Operation
 */
class MakeDir {
    private final DirTree dirTree;
    private final EventBus bus;
//    private Subscription subscription;

    MakeDir(DirTree dirTree, EventBus bus) {
        this.dirTree = dirTree;
        this.bus = bus;
    }

    void process(File rootDir, String name) {
//        cancelPrevOp();

        Observable<File> observable = new MakeDirObservable().create(rootDir, name);
        Observer<File> observer = new MakeDirObserver(dirTree, bus);

/*        subscription = */ observable.observeOn(Schedulers.io())
//                .onBackpressureDrop()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(observer);
    }
/*
    private void cancelPrevOp() {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
        subscription = null;
    }
*/
}
