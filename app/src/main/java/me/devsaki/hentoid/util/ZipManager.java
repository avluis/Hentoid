package me.devsaki.hentoid.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by avluis on 8/25/15.
 * General Compression/Extraction utility.
 */
public class ZipManager {
    private static final String TAG = LogHelper.makeLogTag(ZipManager.class);

    private static final int BUFFER = 20480;

    public void zipFiles(String[] files, String zipFileName) {
        try {
            BufferedInputStream input;
            FileOutputStream destination = new FileOutputStream(zipFileName);
            ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(
                    destination));
            byte data[] = new byte[BUFFER];

            for (String file : files) {
                LogHelper.d(TAG, "Compressing File: " + file);

                FileInputStream inputStream = new FileInputStream(file);
                input = new BufferedInputStream(inputStream, BUFFER);

                ZipEntry zipEntry = new ZipEntry(file.substring(file.lastIndexOf("/") + 1));
                zipOut.putNextEntry(zipEntry);
                int count;
                while ((count = input.read(data, 0, BUFFER)) != -1) {
                    zipOut.write(data, 0, count);
                }
                input.close();
            }
            zipOut.close();
        } catch (Exception e) {
            LogHelper.e(TAG, "Compression Failed: ", e);
        }
    }

    public void unzipFile(String zipFile, String targetLocation) {
        dirChecker(targetLocation);

        try {
            FileInputStream fileInput = new FileInputStream(zipFile);
            ZipInputStream zipInput = new ZipInputStream(fileInput);
            ZipEntry zipEntry;
            while ((zipEntry = zipInput.getNextEntry()) != null) {
                LogHelper.d(TAG, "Extracting File: " + zipEntry.getName());

                if (zipEntry.isDirectory()) {
                    dirChecker(zipEntry.getName());
                } else {
                    FileOutputStream fileOut =
                            new FileOutputStream(targetLocation + zipEntry.getName());
                    for (int c = zipInput.read(); c != -1; c = zipInput.read()) {
                        fileOut.write(c);
                    }
                    zipInput.closeEntry();
                    fileOut.close();
                }
            }
            zipInput.close();
        } catch (Exception e) {
            LogHelper.e(TAG, "Extraction Failed: ", e);
        }
    }

    private void dirChecker(String dir) {
        File file = new File(dir);
        if (!file.isDirectory()) {
            final boolean mkdirs = file.mkdirs();
            if (mkdirs) {
                LogHelper.d(TAG, "Directory Created.");
            }
        }
    }
}