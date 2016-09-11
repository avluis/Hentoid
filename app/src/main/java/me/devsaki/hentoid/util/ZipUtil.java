package me.devsaki.hentoid.util;

import android.os.AsyncTask;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by avluis on 09/11/2016.
 * Zip Utility
 * </p>
 * TODO: Link with FileHelper for SAF safe operations
 */

public class ZipUtil {
    private static final String TAG = LogHelper.makeLogTag(ZipUtil.class);

    static class UnZipTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String filePath = params[0];
            String destinationPath = params[1];

            File archive = new File(filePath);
            try {
                ZipFile zipfile = new ZipFile(archive);
                for (Enumeration e = zipfile.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = (ZipEntry) e.nextElement();
                    unzipEntry(zipfile, entry, destinationPath);
                }
                zipfile.close();
            } catch (Exception e) {
                LogHelper.e(TAG, "Error while extracting file: " + archive, e);
                return false;
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            LogHelper.d(TAG, "All files extracted without error: " + aBoolean);
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

            BufferedInputStream inputStream = new BufferedInputStream(
                    zipfile.getInputStream(entry));
            BufferedOutputStream outputStream = new BufferedOutputStream(
                    new FileOutputStream(outputFile));

            try {
                IOUtils.copy(inputStream, outputStream);
            } finally {
                try {
                    outputStream.close();
                    inputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        private void createDir(File dir) {
            if (dir.exists()) {
                return;
            }
            LogHelper.d(TAG, "Creating dir: " + dir.getName());
            if (!dir.mkdirs()) {
                LogHelper.w(TAG, "Could not create dir: " + dir);
            }
        }
    }
}
