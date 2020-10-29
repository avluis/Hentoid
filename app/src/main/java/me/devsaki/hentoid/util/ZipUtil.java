package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import me.devsaki.hentoid.database.domains.ImageFile;
import timber.log.Timber;

/**
 * Created by avluis on 09/11/2016.
 * Zip Utility
 */

public class ZipUtil {

    private ZipUtil() {
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
    public static boolean isArchiveExtensionSupported(String extension) {
        return extension.equalsIgnoreCase("zip")
                || extension.equalsIgnoreCase("cbr")
                || extension.equalsIgnoreCase("epub")
                || extension.equalsIgnoreCase("cbz");
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
