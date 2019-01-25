package me.devsaki.hentoid.dirpicker.observable;

import java.io.File;

import io.reactivex.Observable;
import io.reactivex.functions.Predicate;


/**
 * Created by avluis on 06/12/2016.
 * List Directory Observable
 */
public class ListDirObservable extends ListFileObservable {

    public Observable<File> create(final File rootDir) {
        return super.create(rootDir).filter(isDir());
    }

    private Predicate<File> isDir() {
        return File::isDirectory;
    }
}
