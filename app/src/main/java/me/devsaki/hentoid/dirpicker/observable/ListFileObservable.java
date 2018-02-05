package me.devsaki.hentoid.dirpicker.observable;

import java.io.File;

import rx.Observable;

/**
 * Created by avluis on 06/12/2016.
 * List File Observable
 */
class ListFileObservable {

    Observable<File> create(final File rootDir) {
        return Observable.unsafeCreate(subscriber -> {
            File[] childDirs = rootDir.listFiles();

            for (File child : childDirs) {
                subscriber.onNext(child);
            }
            subscriber.onCompleted();
        });
    }
}
