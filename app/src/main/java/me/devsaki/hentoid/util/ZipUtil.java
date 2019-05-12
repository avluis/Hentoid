package me.devsaki.hentoid.util;

import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import timber.log.Timber;

/**
 * Created by avluis on 09/11/2016.
 * Zip Utility
 */

class ZipUtil {

    private static final int BUFFER = 32 * 1024;

    private static void add(final File file, final ZipOutputStream stream, final byte[] data) {
        Timber.d("Adding: %s", file);
        try (FileInputStream fi = new FileInputStream(file); BufferedInputStream origin = new BufferedInputStream(fi, BUFFER)) {

            ZipEntry zipEntry = new ZipEntry(file.getName());
            stream.putNextEntry(zipEntry);
            int count;

            while ((count = origin.read(data, 0, BUFFER)) != -1) {
                stream.write(data, 0, count);
            }
        } catch (FileNotFoundException e) {
            Timber.e(e, "File Not Found: %s", file);
        } catch (IOException e) {
            Timber.e(e, "IO Exception: %s", file);
        }
    }

    abstract static class ZipTask extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Object... params) {
            File[] files = (File[]) params[0];
            File dest = (File) params[1];
            try (FileOutputStream out = new FileOutputStream(dest); ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(out))) {
                final byte[] data = new byte[BUFFER];
                for (File file : files) add(file, zipOutputStream, data);
                FileUtil.sync(out);
                out.flush();
            } catch (Exception e) {
                Timber.e(e, "Error while zipping resources");
                return false;
            }

            return true;
        }
    }

    static class UnZipTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String filePath = params[0];
            String destinationPath = params[1];

            File archive = new File(filePath);
            try (ZipFile zipfile = new ZipFile(archive)) {
                for (Enumeration e = zipfile.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = (ZipEntry) e.nextElement();
                    unzipEntry(zipfile, entry, destinationPath);
                }
            } catch (Exception e) {
                Timber.e(e, "Error while extracting file: %s", archive);
                return false;
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            Timber.d("All files extracted without error: %s", aBoolean);
        }

        private void unzipEntry(ZipFile zipfile, ZipEntry entry, String outputDir)
                throws IOException {

            if (entry.isDirectory()) {
                createDir(new File(outputDir, entry.getName()));
                return;
            }

            File outputFile = new File(outputDir, entry.getName());
            if (!outputFile.getParentFile().exists()) {
                createDir(outputFile.getParentFile());
            }

            try (BufferedInputStream in = new BufferedInputStream(zipfile.getInputStream(entry)); BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                byte[] buffer = new byte[BUFFER];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                FileHelper.sync(out);
                out.flush();
            }
            // Ignore
        }

        private void createDir(File dir) {
            if (dir.exists()) {
                return;
            }
            Timber.d("Creating dir: %s", dir.getName());
            if (!dir.mkdirs()) {
                Timber.w("Could not create dir: %s", dir);
            }
        }
    }
}
