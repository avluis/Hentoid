package me.devsaki.hentoid.dirpicker.ops;

import java.io.File;
import java.io.IOException;

import me.devsaki.hentoid.dirpicker.exceptions.DirExistsException;
import me.devsaki.hentoid.dirpicker.exceptions.PermissionDeniedException;
import me.devsaki.hentoid.util.FileHelper;
import timber.log.Timber;

/**
 * Created by avluis on 06/12/2016.
 * Make Directory Operation
 */
public class MakeDir {

    public static void TryMakeDir(File rootDir, String dirName) throws IOException {
        if (!rootDir.canWrite()) {
            throw new PermissionDeniedException();
        }

        File newDir = new File(rootDir, dirName);
        if (newDir.exists()) {
            throw new DirExistsException();
        } else {
            boolean isDirCreated = FileHelper.createDirectory(newDir);
            if (isDirCreated) {
                return;
            } else {
                throw new IOException();
            }
        }
    }
}
