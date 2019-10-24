package me.devsaki.hentoid.util;

import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import timber.log.Timber;

/**
 * Created by avluis on 09/11/2016.
 * Zip Utility
 */

public class ZipUtil {

    private ZipUtil() {
        throw new IllegalStateException("Utility class");
    }

    private static final int BUFFER = 32 * 1024;

    @WorkerThread
    public static File zipFiles(List<File> files, File dest) throws IOException {
        try (FileOutputStream out = new FileOutputStream(dest); ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(out))) {
            final byte[] data = new byte[BUFFER];
            for (File file : files) addFile(file, zipOutputStream, data);
            FileUtil.sync(out);
            out.flush();
        }
        return dest;
    }

    private static void addFile(final File file, final ZipOutputStream stream, final byte[] data) throws IOException {
        Timber.d("Adding: %s", file);
        try (FileInputStream fi = new FileInputStream(file); BufferedInputStream origin = new BufferedInputStream(fi, BUFFER)) {

            ZipEntry zipEntry = new ZipEntry(file.getName());
            stream.putNextEntry(zipEntry);
            int count;

            while ((count = origin.read(data, 0, BUFFER)) != -1) {
                stream.write(data, 0, count);
            }
        }
    }
}
