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

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
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

    private static final FileHelper.NameFilter archiveNamesFilter = displayName -> isArchiveExtensionSupported(FileHelper.getExtension(displayName));

    private static final int BUFFER = 32 * 1024;


    /**
     * Determine if the given file extension is supported by the app as an archive
     *
     * @param extension File extension to test
     * @return True if the app supports the reading of files with the given extension as archives; false if not
     */
    public static boolean isArchiveExtensionSupported(@NonNull final String extension) {
        return extension.equalsIgnoreCase("zip")
                || extension.equalsIgnoreCase("epub")
                || extension.equalsIgnoreCase("cbz")
                || extension.equalsIgnoreCase("cbr")
                || extension.equalsIgnoreCase("rar");
    }

    /**
     * Determine if the given file name is supported by the app as an archive
     *
     * @param fileName File name to test
     * @return True if the app supports the reading of the given file name as an archive; false if not
     */
    public static boolean isSupportedArchive(@NonNull final String fileName) {
        return isArchiveExtensionSupported(FileHelper.getExtension(fileName));
    }

    /**
     * Build a {@link FileHelper.NameFilter} only accepting archive files supported by the app
     *
     * @return {@link FileHelper.NameFilter} only accepting archive files supported by the app
     */
    public static FileHelper.NameFilter getArchiveNamesFilter() {
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

    /**
     * Get the entries of the given archive file
     *
     * @param context Context to be used
     * @param file    Archive file to read
     * @return List of the entries of the given archive file; an empty list if the archive file is not supported
     * @throws IOException If something horrible happens during I/O
     */
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

    /**
     * Get the entries of the given ZIP file
     *
     * @param bis Stream to read from
     * @return List of the entries of the given ZIP file
     * @throws IOException If something horrible happens during I/O
     */
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

    /**
     * Get the entries of the given RAR file
     *
     * @param bis Stream to read from
     * @return List of the entries of the given RAR file
     * @throws IOException If something horrible happens during I/O
     */
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

    /**
     * Extract the given entries from the given archive file
     * This is the variant to be used with RxJava
     *
     * @param context          Context to be used
     * @param file             Archive file to extract from
     * @param entriesToExtract List of entries to extract (relative paths to the archive root); null to extract everything
     * @param targetFolder     Target folder to create the archives into
     * @param targetNames      List of names of the target files (as many entries as the entriesToExtract argument)
     * @return Observable that follows the extraction of each entry
     * @throws IOException If something horrible happens during I/O
     */
    public static Observable<Uri> extractArchiveEntriesRx(
            @NonNull final Context context,
            @NonNull final DocumentFile file,
            @Nullable final List<String> entriesToExtract,
            @NonNull final File targetFolder, // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
            @Nullable final List<String> targetNames) throws IOException {
        Helper.assertNonUiThread();
        try (InputStream fi = FileHelper.getInputStream(context, file); BufferedInputStream bis = new BufferedInputStream(fi, BUFFER)) {
            byte[] header = new byte[4];
            bis.mark(header.length);
            if (bis.read(header) < header.length) return Observable.empty();
            bis.reset();
            String mimeType = getMimeTypeFromArchiveBinary(header);
            if (mimeType.equals(ZIP_MIME_TYPE))
                return Observable.create(emitter -> extractZipEntries(context, file, entriesToExtract, targetFolder, targetNames, emitter));
            else if (mimeType.equals(RAR_MIME_TYPE))
                return Observable.create(emitter -> extractRarEntries(context, file, entriesToExtract, targetFolder, targetNames, emitter));
            else return Observable.empty();
        }
    }

    /**
     * Extract the given entries from the given archive file
     *
     * @param context          Context to be used
     * @param file             Archive file to extract from
     * @param entriesToExtract List of entries to extract (relative paths to the archive root); null to extract everything
     * @param targetFolder     Target folder to create the archives into
     * @param targetNames      List of names of the target files (as many entries as the entriesToExtract argument)
     * @return List of the Uri's of the extracted files
     * @throws IOException If something horrible happens during I/O
     */
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
                return extractZipEntries(bis, entriesToExtract, targetFolder, targetNames, null);
            else if (mimeType.equals(RAR_MIME_TYPE))
                return extractRarEntries(bis, entriesToExtract, targetFolder, targetNames, null);
            else return Collections.emptyList();
        }
    }

    /**
     * Extract the given entries from the given ZIP file
     *
     * @param context          Context to be used
     * @param file             Archive file to extract from
     * @param entriesToExtract List of entries to extract (relative paths to the archive root); null to extract everything
     * @param targetFolder     Target folder to create the archives into
     * @param targetNames      List of names of the target files (as many entries as the entriesToExtract argument)
     * @param emitter          Optional emitter to be used when the method is used with RxJava
     * @throws IOException If something horrible happens during I/O
     */
    private static void extractZipEntries(
            @NonNull final Context context,
            @NonNull final DocumentFile file,
            @Nullable final List<String> entriesToExtract,
            @NonNull final File targetFolder, // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
            @Nullable final List<String> targetNames,
            @Nullable final ObservableEmitter<Uri> emitter) throws IOException {
        try (InputStream fi = FileHelper.getInputStream(context, file); BufferedInputStream bis = new BufferedInputStream(fi, BUFFER)) {
            extractZipEntries(bis, entriesToExtract, targetFolder, targetNames, emitter);
        }
    }

    /**
     * Extract the given entries from the given ZIP file
     *
     * @param bis              Stream to read from
     * @param entriesToExtract List of entries to extract (relative paths to the archive root); null to extract everything
     * @param targetFolder     Target folder to create the archives into
     * @param targetNames      List of names of the target files (as many entries as the entriesToExtract argument)
     * @param emitter          Optional emitter to be used when the method is used with RxJava
     * @return List of the Uri's of the extracted files
     * @throws IOException If something horrible happens during I/O
     */
    private static List<Uri> extractZipEntries(
            @NonNull final BufferedInputStream bis,
            @Nullable final List<String> entriesToExtract,
            @NonNull final File targetFolder, // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
            @Nullable final List<String> targetNames,
            @Nullable final ObservableEmitter<Uri> emitter) throws IOException {
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
                                Timber.w("File already exists : %s", targetFile.getAbsolutePath());
                        } else {
                            targetFile = existing[0];
                        }

                        try (OutputStream out = FileHelper.getOutputStream(targetFile)) {
                            while ((count = input.read(buffer)) != -1)
                                out.write(buffer, 0, count);
                        }
                        result.add(Uri.fromFile(targetFile));
                        if (emitter != null) emitter.onNext(Uri.fromFile(targetFile));
                    }
                    input.closeEntry();
                }
                entry = input.getNextEntry();
            }
        }
        if (emitter != null) emitter.onComplete();
        return result;
    }

    /**
     * Extract the given entries from the given RAR file
     *
     * @param context          Context to be used
     * @param file             Archive file to extract from
     * @param entriesToExtract List of entries to extract (relative paths to the archive root); null to extract everything
     * @param targetFolder     Target folder to create the archives into
     * @param targetNames      List of names of the target files (as many entries as the entriesToExtract argument)
     * @param emitter          Optional emitter to be used when the method is used with RxJava
     * @throws IOException If something horrible happens during I/O
     */
    private static void extractRarEntries(
            @NonNull final Context context,
            @NonNull final DocumentFile file,
            @Nullable final List<String> entriesToExtract,
            @NonNull final File targetFolder, // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
            @Nullable final List<String> targetNames,
            @Nullable final ObservableEmitter<Uri> emitter) throws IOException {
        try (InputStream fi = FileHelper.getInputStream(context, file); BufferedInputStream bis = new BufferedInputStream(fi, BUFFER)) {
            extractRarEntries(bis, entriesToExtract, targetFolder, targetNames, emitter);
        }
    }

    /**
     * Extract the given entries from the given RAR file
     *
     * @param bis              Stream to read from
     * @param entriesToExtract List of entries to extract (relative paths to the archive root); null to extract everything
     * @param targetFolder     Target folder to create the archives into
     * @param targetNames      List of names of the target files (as many entries as the entriesToExtract argument)
     * @param emitter          Optional emitter to be used when the method is used with RxJava
     * @return List of the Uri's of the extracted files
     * @throws IOException If something horrible happens during I/O
     */
    private static List<Uri> extractRarEntries(
            @NonNull final BufferedInputStream bis,
            @Nullable final List<String> entriesToExtract,
            @NonNull final File targetFolder, // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
            @Nullable final List<String> targetNames,
            @Nullable final ObservableEmitter<Uri> emitter) throws IOException {
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
                        if (emitter != null) emitter.onNext(Uri.fromFile(targetFile));
                        result.add(Uri.fromFile(targetFile));
                    }
                }
            }
        } catch (RarException e) {
            Timber.w(e);
        }
        if (emitter != null) emitter.onComplete();
        return result;
    }

    // ================= ZIP FILE CREATION

    /**
     * Archive the given files into the given output stream
     *
     * @param context Context to be used
     * @param files   List of the files to be archived
     * @param out     Output stream to write to
     * @throws IOException If something horrible happens during I/O
     */
    public static void zipFiles(@NonNull final Context context, @NonNull final List<DocumentFile> files, @NonNull final OutputStream out) throws IOException {
        Helper.assertNonUiThread();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(out))) {
            final byte[] data = new byte[BUFFER];
            for (DocumentFile file : files) addFile(context, file, zipOutputStream, data);
            out.flush();
        }
    }

    /**
     * Add the given file to the given ZipOutputStream
     *
     * @param context Context to be used
     * @param file    File to be added
     * @param stream  ZipOutputStream to write to
     * @param buffer  Buffer to be used
     * @throws IOException If something horrible happens during I/O
     */
    private static void addFile(@NonNull final Context context,
                                @NonNull final DocumentFile file,
                                final ZipOutputStream stream,
                                final byte[] buffer) throws IOException {
        Timber.d("Adding: %s", file);
        try (InputStream fi = FileHelper.getInputStream(context, file); BufferedInputStream origin = new BufferedInputStream(fi, BUFFER)) {

            ZipEntry zipEntry = new ZipEntry(file.getName());
            stream.putNextEntry(zipEntry);
            int count;

            while ((count = origin.read(buffer, 0, BUFFER)) != -1) {
                stream.write(buffer, 0, count);
            }
        }
    }

    @SuppressWarnings("squid:S1104")
    // This is a dumb struct class, nothing more
    // Describes an entry inside an archive
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
