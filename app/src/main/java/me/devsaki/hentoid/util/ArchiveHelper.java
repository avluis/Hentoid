package me.devsaki.hentoid.util;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Stream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import timber.log.Timber;

/**
 * Created by avluis on 09/11/2016.
 * Zip Utility
 */

public class ArchiveHelper {

    private ArchiveHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static final String ZIP_MIME_TYPE = "application/zip";
    private static FileHelper.NameFilter archiveNamesFilter;

    private static final int BUFFER = 32 * 1024;


    /**
     * Determine if the given archive file extension is supported by the app
     *
     * @param extension File extension to test
     * @return True if the app supports the reading of files with the given extension; false if not
     */
    public static boolean isArchiveExtensionSupported(@NonNull final String extension) {
        return extension.equalsIgnoreCase("zip")
                || extension.equalsIgnoreCase("epub")
                || extension.equalsIgnoreCase("cbz");
        //|| extension.equalsIgnoreCase("cbr")
        //|| extension.equalsIgnoreCase("rar");
    }

    public static boolean isSupportedArchive(@NonNull final String fileName) {
        return isArchiveExtensionSupported(FileHelper.getExtension(fileName));
    }

    /**
     * Build a {@link FileHelper.NameFilter} only accepting archive files supported by the app
     *
     * @return {@link FileHelper.NameFilter} only accepting archive files supported by the app
     */
    public static FileHelper.NameFilter getArchiveNamesFilter() {
        if (null == archiveNamesFilter)
            archiveNamesFilter = displayName -> isArchiveExtensionSupported(FileHelper.getExtension(displayName));
        return archiveNamesFilter;
    }

    public static List<ZipEntry> getZipEntries(@NonNull final Context context, @NonNull final DocumentFile file) throws IOException {
        Helper.assertNonUiThread();
        List<ZipEntry> result = new ArrayList<>();
        try (InputStream fi = FileHelper.getInputStream(context, file); BufferedInputStream bis = new BufferedInputStream(fi, BUFFER); ZipInputStream input = new ZipInputStream(bis)) {
            ZipEntry entry = input.getNextEntry();
            while (entry != null) {
                result.add(entry);
                entry = input.getNextEntry();
            }
        }
        return result;
    }

    public static List<Uri> extractZipEntries(
            @NonNull final Context context,
            @NonNull final DocumentFile zipFile,
            @Nullable final List<String> entriesToExtract,
            @NonNull final File targetFolder, // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
            @Nullable final List<String> targetNames) throws IOException {
        Helper.assertNonUiThread();
        List<Uri> result = new ArrayList<>();
        int index = 0;

        try (InputStream fi = FileHelper.getInputStream(context, zipFile); BufferedInputStream bis = new BufferedInputStream(fi, BUFFER); ZipInputStream input = new ZipInputStream(bis)) {
            byte[] buffer = new byte[BUFFER];
            ZipEntry entry = input.getNextEntry();
            while (entry != null) {
                final ZipEntry theEntry = entry;
                if (null == entriesToExtract || Stream.of(entriesToExtract).anyMatch(e -> e.equalsIgnoreCase(theEntry.getName()))) {
                    int count;
                    // TL;DR - We don't care about folders
                    // If we were coding an all-purpose extractor we would have to create folders
                    // But Hentoid just wants to extract a bunch of files in one single place !

                    String fileName;
                    if (null == targetNames) {
                        fileName = theEntry.getName();
                        int lastSeparator = fileName.lastIndexOf(File.separator);
                        if (lastSeparator > -1) fileName = fileName.substring(lastSeparator + 1);
                    } else {
                        fileName = targetNames.get(index++) + "." + FileHelper.getExtension(theEntry.getName());
                    }
                    final String fileNameFinal = fileName;

                    File targetFile;
                    File[] existing = targetFolder.listFiles((dir, name) -> name.equalsIgnoreCase(fileNameFinal));
                    if (0 == existing.length) {
                        targetFile = new File(targetFolder.getAbsolutePath() + File.separator + fileName);
                        if (!targetFile.createNewFile())
                            throw new IOException("Could not create file " + targetFile.getPath());
                    } else {
                        targetFile = existing[0];
                    }

                    try (OutputStream out = FileHelper.getOutputStream(targetFile)) {
                        while ((count = input.read(buffer)) != -1)
                            out.write(buffer, 0, count);
                    }
                    input.closeEntry();

                    result.add(Uri.fromFile(targetFile));
                }
                entry = input.getNextEntry();
            }
        }
        return result;
    }

    // ================= ZIP FILE CREATION

    public static void zipFiles(@NonNull final Context context, @NonNull final List<DocumentFile> files, @NonNull final OutputStream out) throws IOException {
        Helper.assertNonUiThread();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(out))) {
            final byte[] data = new byte[BUFFER];
            for (DocumentFile file : files) addFile(context, file, zipOutputStream, data);
            out.flush();
        }
    }

    private static void addFile(@NonNull final Context context,
                                @NonNull final DocumentFile file,
                                final ZipOutputStream stream,
                                final byte[] data) throws IOException {
        Timber.d("Adding: %s", file);
        try (InputStream fi = FileHelper.getInputStream(context, file); BufferedInputStream origin = new BufferedInputStream(fi, BUFFER)) {

            ZipEntry zipEntry = new ZipEntry(file.getName());
            stream.putNextEntry(zipEntry);
            int count;

            while ((count = origin.read(data, 0, BUFFER)) != -1) {
                stream.write(data, 0, count);
            }
        }
    }
}
