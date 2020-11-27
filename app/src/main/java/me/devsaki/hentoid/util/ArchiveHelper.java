package me.devsaki.hentoid.util;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Stream;
import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
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
    public static final String RAR_MIME_TYPE = "application/x-rar-compressed";
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
                || extension.equalsIgnoreCase("cbz")
                || extension.equalsIgnoreCase("cbr")
                || extension.equalsIgnoreCase("rar");
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

    /**
     * Determine the MIME-type of the given binary data if it's an archive
     *
     * @param binary Achive binary data to determine the MIME-type for
     * @return MIME-type of the given binary data; empty string if not supported
     */
    public static String getMimeTypeFromArchiveBinary(byte[] binary) {
        if (binary.length < 4) return "";

        // In Java, byte type is signed !
        // => Converting all raw values to byte to be sure they are evaluated as expected
        if ((byte) 0x50 == binary[0] && (byte) 0x4B == binary[1] && (byte) 0x03 == binary[2])
            return ZIP_MIME_TYPE;
        else if ((byte) 0x52 == binary[0] && (byte) 0x61 == binary[1] && (byte) 0x72 == binary[2] && (byte) 0x21 == binary[3])
            return RAR_MIME_TYPE;
        else return "";
    }

    // TODO doc
    public static List<ArchiveEntry> getArchiveEntries(@NonNull final Context context, @NonNull final DocumentFile file) throws IOException {
        Helper.assertNonUiThread();
        try (InputStream fi = FileHelper.getInputStream(context, file); BufferedInputStream bis = new BufferedInputStream(fi, BUFFER)) {
            byte[] header = new byte[4];
            bis.mark(header.length);
            if (bis.read(header) < header.length) return Collections.emptyList();
            bis.reset();
            String mimeType = getMimeTypeFromArchiveBinary(header);
            if (mimeType.equals(ZIP_MIME_TYPE)) return getZipEntries(bis);
            else if (mimeType.equals(RAR_MIME_TYPE)) return getRarEntries(bis);
            else return Collections.emptyList();
        }
    }

    // TODO doc
    private static List<ArchiveEntry> getZipEntries(@NonNull final BufferedInputStream bis) throws IOException {
        Helper.assertNonUiThread();
        List<ArchiveEntry> result = new ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(bis)) {
            ZipEntry entry = input.getNextEntry();
            while (entry != null) {
                result.add(ArchiveEntry.fromZipEntry(entry));
                entry = input.getNextEntry();
            }
        }
        return result;
    }

    // TODO doc
    private static List<ArchiveEntry> getRarEntries(@NonNull final BufferedInputStream bis) throws IOException {
        Helper.assertNonUiThread();
        List<ArchiveEntry> result = new ArrayList<>();
        try (Archive input = new Archive(bis)) {
            if (input.isEncrypted()) {
                Timber.w("archive is encrypted cannot extract");
                return result;
            }
            for (final FileHeader fileHeader : input) {
                result.add(ArchiveEntry.fromRarEntry(fileHeader));
            }
        } catch (RarException e) {
            Timber.w(e);
        }
        return result;
    }

    // TODO doc
    public static List<Uri> extractArchiveEntries(
            @NonNull final Context context,
            @NonNull final DocumentFile file,
            @Nullable final List<String> entriesToExtract,
            @NonNull final File targetFolder, // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
            @Nullable final List<String> targetNames) throws IOException {
        Helper.assertNonUiThread();
        try (InputStream fi = FileHelper.getInputStream(context, file); BufferedInputStream bis = new BufferedInputStream(fi, BUFFER)) {
            byte[] header = new byte[4];
            bis.mark(header.length);
            if (bis.read(header) < header.length) return Collections.emptyList();
            bis.reset();
            String mimeType = getMimeTypeFromArchiveBinary(header);
            if (mimeType.equals(ZIP_MIME_TYPE))
                return extractZipEntries(bis, entriesToExtract, targetFolder, targetNames);
            else if (mimeType.equals(RAR_MIME_TYPE))
                return extractRarEntries(bis, entriesToExtract, targetFolder, targetNames);
            else return Collections.emptyList();
        }
    }

    // TODO doc
    private static List<Uri> extractZipEntries(
            @NonNull final BufferedInputStream bis,
            @Nullable final List<String> entriesToExtract,
            @NonNull final File targetFolder, // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
            @Nullable final List<String> targetNames) throws IOException {
        Helper.assertNonUiThread();
        List<Uri> result = new ArrayList<>();
        int index = 0;

        try (ZipInputStream input = new ZipInputStream(bis)) {
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
                    if (existing != null) {
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
                        result.add(Uri.fromFile(targetFile));
                    }
                    input.closeEntry();
                }
                entry = input.getNextEntry();
            }
        }
        return result;
    }

    // TODO doc
    private static List<Uri> extractRarEntries(
            @NonNull final BufferedInputStream bis,
            @Nullable final List<String> entriesToExtract,
            @NonNull final File targetFolder, // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
            @Nullable final List<String> targetNames) throws IOException {
        Helper.assertNonUiThread();
        List<Uri> result = new ArrayList<>();
        int index = 0;

        try (Archive input = new Archive(bis)) {
            byte[] buffer = new byte[BUFFER];
            for (final FileHeader fileHeader : input) {
                if (null == entriesToExtract || Stream.of(entriesToExtract).anyMatch(e -> e.equalsIgnoreCase(fileHeader.getFileName()))) {
                    int count;
                    // TL;DR - We don't care about folders
                    // If we were coding an all-purpose extractor we would have to create folders
                    // But Hentoid just wants to extract a bunch of files in one single place !

                    String fileName;
                    if (null == targetNames) {
                        fileName = fileHeader.getFileName();
                        int lastSeparator = fileName.lastIndexOf(File.separator);
                        if (lastSeparator > -1) fileName = fileName.substring(lastSeparator + 1);
                    } else {
                        fileName = targetNames.get(index++) + "." + FileHelper.getExtension(fileHeader.getFileName());
                    }
                    final String fileNameFinal = fileName;

                    File targetFile;
                    File[] existing = targetFolder.listFiles((dir, name) -> name.equalsIgnoreCase(fileNameFinal));
                    if (existing != null) {
                        if (0 == existing.length) {
                            targetFile = new File(targetFolder.getAbsolutePath() + File.separator + fileName);
                            if (!targetFile.createNewFile())
                                throw new IOException("Could not create file " + targetFile.getPath());
                        } else {
                            targetFile = existing[0];
                        }

                        try (OutputStream out = FileHelper.getOutputStream(targetFile); InputStream entryInput = input.getInputStream(fileHeader)) {
                            while ((count = entryInput.read(buffer)) != -1)
                                out.write(buffer, 0, count);
                        }
                        result.add(Uri.fromFile(targetFile));
                    }
                }
            }
        } catch (RarException e) {
            Timber.w(e);
        }
        return result;
    }

    // ================= ZIP FILE CREATION

    // TODO doc
    public static void zipFiles(@NonNull final Context context, @NonNull final List<DocumentFile> files, @NonNull final OutputStream out) throws IOException {
        Helper.assertNonUiThread();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(out))) {
            final byte[] data = new byte[BUFFER];
            for (DocumentFile file : files) addFile(context, file, zipOutputStream, data);
            out.flush();
        }
    }

    // TODO doc
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

    @SuppressWarnings("squid:S1104")
    // This is a dumb struct class, nothing more
    public static class ArchiveEntry {
        public String path;
        public long size;

        public ArchiveEntry(String path, long size) {
            this.path = path;
            this.size = size;
        }

        public static ArchiveEntry fromZipEntry(ZipEntry entry) {
            return new ArchiveEntry(entry.getName(), entry.getSize());
        }

        public static ArchiveEntry fromRarEntry(FileHeader entry) {
            return new ArchiveEntry(entry.getFileName(), entry.getUnpSize());
        }
    }
}
