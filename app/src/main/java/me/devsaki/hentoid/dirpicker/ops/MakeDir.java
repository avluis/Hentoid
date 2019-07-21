package me.devsaki.hentoid.dirpicker.ops;

import java.io.File;
import java.io.IOException;

import me.devsaki.hentoid.dirpicker.exceptions.DirExistsException;
import me.devsaki.hentoid.dirpicker.exceptions.PermissionDeniedException;
import me.devsaki.hentoid.util.FileHelper;

/**
 * Created by avluis on 06/12/2016.
 * Make Directory Operation
 */
public class MakeDir {

    public static void tryMakeDir(File rootDir, String dirName) throws IOException {
        if (!rootDir.canWrite()) {
            throw new PermissionDeniedException();
        }

        File newDir = new File(rootDir, dirName);
        if (newDir.exists()) {
            throw new DirExistsException();
        } else {
            if (!FileHelper.createDirectory(newDir))
                throw new IOException();
        }
    }
}
