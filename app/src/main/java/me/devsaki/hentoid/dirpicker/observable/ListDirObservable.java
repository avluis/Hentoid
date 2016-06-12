package me.devsaki.hentoid.dirpicker.observable;

import java.io.File;

import rx.Observable;
import rx.functions.Func1;

/**
 * Created by avluis on 06/12/2016.
 * List Directory Observable
 */
public class ListDirObservable extends ListFileObservable {

    public Observable<File> create(final File rootDir) {
        return super.create(rootDir).filter(isDir());
    }

    private Func1<File, Boolean> isDir() {
        return new Func1<File, Boolean>() {
            @Override
            public Boolean call(File file) {
                return file.isDirectory();
            }
        };
    }
}
