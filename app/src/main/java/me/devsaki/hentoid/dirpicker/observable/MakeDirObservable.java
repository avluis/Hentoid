package me.devsaki.hentoid.dirpicker.observable;

import java.io.File;
import java.io.IOException;

import me.devsaki.hentoid.dirpicker.exceptions.DirExistsException;
import me.devsaki.hentoid.dirpicker.exceptions.PermissionDeniedException;
import rx.Observable;

/**
 * Created by avluis on 06/12/2016.
 * Make (create) Directory Observable
 */
public class MakeDirObservable {

    public Observable<File> create(final File rootDir, final String dirName) {
        return Observable.unsafeCreate(subscriber -> {
            if (!rootDir.canWrite()) {
                subscriber.onError(new PermissionDeniedException());
            }

            File newDir = new File(rootDir, dirName);
            if (newDir.exists()) {
                subscriber.onError(new DirExistsException());
            } else {
                boolean isDirCreated = newDir.mkdir();
                if (isDirCreated) {
                    subscriber.onNext(newDir);
                    subscriber.onCompleted();
                } else {
                    subscriber.onError(new IOException());
                }
            }
        });
    }
}
