package me.devsaki.hentoid.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Stream;

import net.sf.sevenzipjbinding.ArchiveFormat;
import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IArchiveOpenCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.ISeekableStream;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
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

    private static final FileHelper.NameFilter archiveNamesFilter = displayName -> isArchiveExtensionSupported(FileHelper.getExtension(displayName));
    private static final String CACHE_SEPARATOR = "£";

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
                || extension.equalsIgnoreCase("cb7")
                || extension.equalsIgnoreCase("7z")
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
     * Determine the format of the given binary data if it's an archive
     *
     * @param binary Archive binary header data to determine the format for
     * @return Format of the given binary data; null if not supported
     */
    @Nullable
    private static ArchiveFormat getTypeFromArchiveHeader(byte[] binary) {
        if (binary.length < 8) return null;

        // In Java, byte type is signed !
        // => Converting all raw values to byte to be sure they are evaluated as expected
        if ((byte) 0x50 == binary[0] && (byte) 0x4B == binary[1] && (byte) 0x03 == binary[2])
            return ArchiveFormat.ZIP;
        else if ((byte) 0x37 == binary[0] && (byte) 0x7A == binary[1] && (byte) 0xBC == binary[2] && (byte) 0xAF == binary[3] && (byte) 0x27 == binary[4] && (byte) 0x1C == binary[5])
            return ArchiveFormat.SEVEN_ZIP;
        else if ((byte) 0x52 == binary[0] && (byte) 0x61 == binary[1] && (byte) 0x72 == binary[2] && (byte) 0x21 == binary[3] && (byte) 0x1A == binary[4] && (byte) 0x07 == binary[5] && (byte) 0x01 == binary[6] && (byte) 0x00 == binary[7])
            return ArchiveFormat.RAR5;
        else if ((byte) 0x52 == binary[0] && (byte) 0x61 == binary[1] && (byte) 0x72 == binary[2] && (byte) 0x21 == binary[3])
            return ArchiveFormat.RAR;
        else return null;
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

        ArchiveFormat format;
        try (InputStream fi = FileHelper.getInputStream(context, file)) {
            byte[] header = new byte[8];
            if (fi.read(header) < header.length) return Collections.emptyList();
            format = getTypeFromArchiveHeader(header);
        }
        if (null == format) return Collections.emptyList();

        return getArchiveEntries(context, format, file.getUri());
    }

    /**
     * Get the entries of the given archive file
     */
    private static List<ArchiveEntry> getArchiveEntries(@NonNull final Context context, ArchiveFormat format, @NonNull final Uri uri) throws IOException {
        Helper.assertNonUiThread();
        ArchiveOpenCallback callback = new ArchiveOpenCallback();
        List<ArchiveEntry> result = new ArrayList<>();
        try (DocumentFileRandomInStream stream = new DocumentFileRandomInStream(context, uri); IInArchive inArchive = SevenZip.openInArchive(format, stream, callback)) {
            int itemCount = inArchive.getNumberOfItems();
            for (int i = 0; i < itemCount; i++) {
                result.add(new ArchiveEntry(inArchive.getStringProperty(i, PropID.PATH), Integer.parseInt(inArchive.getStringProperty(i, PropID.SIZE))));
            }
        } catch (SevenZipException e) {
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

        if (entriesToExtract != null && entriesToExtract.isEmpty()) return Observable.empty();

        ArchiveFormat format;
        try (InputStream fi = FileHelper.getInputStream(context, file)) {
            byte[] header = new byte[8];
            if (fi.read(header) < header.length) return Observable.empty();
            format = getTypeFromArchiveHeader(header);
        }
        if (null == format) return Observable.empty();

        return Observable.create(emitter -> extractArchiveEntries(context, file.getUri(), format, entriesToExtract, targetFolder, targetNames, emitter));
    }

    /**
     * Extract the given entries from the given archive file
     *
     * @param context          Context to be used
     * @param uri              Uri of the archive file to extract from
     * @param format           Format of the archive file to extract from
     * @param entriesToExtract List of entries to extract (relative paths to the archive root); null to extract everything
     * @param targetFolder     Target folder to create the archives into
     * @param targetNames      List of names of the target files (as many entries as the entriesToExtract argument)
     * @param emitter          Optional emitter to be used when the method is used with RxJava
     * @throws IOException If something horrible happens during I/O
     */
    private static void extractArchiveEntries(
            @NonNull final Context context,
            @NonNull final Uri uri,
            final ArchiveFormat format,
            @Nullable final List<String> entriesToExtract,
            @NonNull final File targetFolder, // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
            @Nullable final List<String> targetNames,
            @Nullable final ObservableEmitter<Uri> emitter) throws IOException {
        Helper.assertNonUiThread();
        int targetIndex = 0;

        Map<Integer, String> fileNames = new HashMap<>();

        // TODO handle the case where the extracted elements would saturate disk space
        try (DocumentFileRandomInStream stream = new DocumentFileRandomInStream(context, uri); IInArchive inArchive = SevenZip.openInArchive(format, stream)) {
            int itemCount = inArchive.getNumberOfItems();
            for (int index = 0; index < itemCount; index++) {
                String fileName = inArchive.getStringProperty(index, PropID.PATH);
                final String fileNameFinal1 = fileName;
                if (null == entriesToExtract || Stream.of(entriesToExtract).anyMatch(e -> e.equalsIgnoreCase(fileNameFinal1))) {
                    // TL;DR - We don't care about folders
                    // If we were coding an all-purpose extractor we would have to create folders
                    // But Hentoid just wants to extract a bunch of files in one single place !

                    if (null == targetNames) {
                        int lastSeparator = fileName.lastIndexOf(File.separator);
                        if (lastSeparator > -1) fileName = fileName.substring(lastSeparator + 1);
                    } else {
                        fileName = targetNames.get(targetIndex++) + "." + FileHelper.getExtension(fileName);
                    }

                    fileNames.put(index, fileName);
                }
            }

            ArchiveExtractCallback callback = new ArchiveExtractCallback(targetFolder, fileNames, emitter);
            int[] indexes = Helper.getPrimitiveLongArrayFromInt(fileNames.keySet());
            inArchive.extract(indexes, false, callback);
        } catch (SevenZipException e) {
            Timber.w(e);
        }
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

    // TODO doc
    // Not to overwrite files with the same name if they are located in different folders
    static String formatCacheFileName(int index, @NonNull final String fileName) {
        return index + CACHE_SEPARATOR + fileName;
    }

    public static String extractCacheFileName(@NonNull final String path) {
        String result = FileHelper.getFileNameWithoutExtension(path);

        int folderSeparatorIndex = result.lastIndexOf(ArchiveHelper.CACHE_SEPARATOR);

        if (-1 == folderSeparatorIndex) return result;
        else return result.substring(folderSeparatorIndex + 1);
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
    }

    private static class ArchiveOpenCallback implements IArchiveOpenCallback {
        @Override
        public void setTotal(Long files, Long bytes) {
            Timber.v("Archive open, total work: " + files + " files, " + bytes + " bytes");
        }

        @Override
        public void setCompleted(Long files, Long bytes) {
            Timber.v("Archive open, completed: " + files + " files, " + bytes + " bytes");
        }
    }

    // https://stackoverflow.com/a/28805474/8374722; https://stackoverflow.com/questions/28897329/documentfile-randomaccessfile
    public static class DocumentFileRandomInStream implements IInStream {

        private ContentResolver contentResolver;
        private Uri uri;

        private ParcelFileDescriptor pfdInput = null;
        private FileInputStream stream = null;

        private long streamSize;
        private long position;

        public DocumentFileRandomInStream(@NonNull final Context context, @NonNull final Uri uri) {
            try {
                this.contentResolver = context.getContentResolver();
                this.uri = uri;
                openUri();
                streamSize = stream.getChannel().size();
            } catch (IOException e) {
                Timber.e(e);
            }
        }

        private void openUri() throws IOException {
            if (stream != null) stream.close();
            if (pfdInput != null) pfdInput.close();

            pfdInput = contentResolver.openFileDescriptor(uri, "r");
            if (pfdInput != null)
                stream = new FileInputStream(pfdInput.getFileDescriptor());
        }

        @Override
        public long seek(long offset, int seekOrigin) throws SevenZipException {
            long seekDelta = 0;
            if (seekOrigin == ISeekableStream.SEEK_CUR) seekDelta = offset;
            else if (seekOrigin == ISeekableStream.SEEK_SET) seekDelta = offset - position;
            else if (seekOrigin == ISeekableStream.SEEK_END)
                seekDelta = streamSize + offset - position;

            if (position + seekDelta > streamSize) position = streamSize;

            if (seekDelta != 0) {
                try {
                    if (seekDelta < 0) {
                        openUri();
                        skipNBytes(position + seekDelta);
                    } else {
                        skipNBytes(seekDelta);
                    }
                } catch (IOException e) {
                    throw new SevenZipException(e);
                }
            }
            position += seekDelta;
            return position;
        }

        // Taken from Java14's InputStream
        // as basic skip is limited by the size of its buffer
        private void skipNBytes(long n) throws IOException {
            if (n > 0) {
                long ns = stream.skip(n);
                if (ns < n) { // skipped too few bytes
                    // adjust number to skip
                    n -= ns;
                    // read until requested number skipped or EOS reached
                    while (n > 0 && stream.read() != -1) {
                        n--;
                    }
                    // if not enough skipped, then EOFE
                    if (n != 0) {
                        throw new EOFException();
                    }
                } else if (ns != n) { // skipped negative or too many bytes
                    throw new IOException("Unable to skip exactly");
                }
            }
        }

        @Override
        public int read(byte[] bytes) throws SevenZipException {
            try {
                int result = stream.read(bytes);
                position += result;
                if (result != bytes.length)
                    Timber.w("diff %s expected; %s read", bytes.length, result);
                if (result < 0) result = 0;
                return result;
            } catch (IOException e) {
                throw new SevenZipException(e);
            }
        }

        @Override
        public void close() throws IOException {
            stream.close();
            pfdInput.close();
        }
    }

    private static class ArchiveExtractCallback implements IArchiveExtractCallback {

        private final File targetFolder;
        private final Map<Integer, String> fileNames;
        private final ObservableEmitter<Uri> emitter;

        private int nbProcessed;
        private ExtractAskMode extractAskMode;
        private SequentialOutStream stream;

        public ArchiveExtractCallback(
                @NonNull final File targetFolder,
                @NonNull final Map<Integer, String> fileNames,
                @Nullable final ObservableEmitter<Uri> emitter) {
            this.targetFolder = targetFolder;
            this.fileNames = fileNames;
            this.emitter = emitter;
            nbProcessed = 0;
        }

        @Override
        public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
            Timber.v("Extract archive, get stream: " + index + " to: " + extractAskMode);

            this.extractAskMode = extractAskMode;
            String fileName = fileNames.get(index);
            if (null == fileName) return null;

            final String targetFileName = formatCacheFileName(index, fileName);
            File[] existing = targetFolder.listFiles((dir, name) -> name.equalsIgnoreCase(targetFileName));
            try {
                if (existing != null) {
                    File targetFile;
                    if (0 == existing.length) {
                        targetFile = new File(targetFolder.getAbsolutePath() + File.separator + targetFileName);
                        if (!targetFile.createNewFile())
                            throw new IOException("Could not create file " + targetFile.getPath());
                    } else {
                        targetFile = existing[0];
                    }
                    if (emitter != null) emitter.onNext(Uri.fromFile(targetFile));

                    stream = new SequentialOutStream(FileHelper.getOutputStream(targetFile));
                    return stream;
                } else {
                    throw new SevenZipException("An I/O error occurred while listing files");
                }
            } catch (IOException e) {
                throw new SevenZipException(e);
            }
        }

        @Override
        public void prepareOperation(ExtractAskMode extractAskMode) {
            Timber.v("Extract archive, prepare to: %s", extractAskMode);
        }

        @Override
        public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
            Timber.v("Extract archive, %s completed with: %s", extractAskMode, extractOperationResult);

            if (extractAskMode != null && extractAskMode.equals(ExtractAskMode.EXTRACT)) {
                nbProcessed++;
                if (nbProcessed == fileNames.size() && emitter != null) emitter.onComplete();
            }

            try {
                if (stream != null) stream.close();
                stream = null;
            } catch (IOException e) {
                throw new SevenZipException(e);
            }

            if (extractOperationResult != ExtractOperationResult.OK) {
                throw new SevenZipException(extractOperationResult.toString());
            }
        }

        @Override
        public void setTotal(long total) {
            Timber.v("Extract archive, work planned: %s", total);
        }

        @Override
        public void setCompleted(long complete) {
            Timber.v("Extract archive, work completed: %s", complete);
        }
    }

    private static class SequentialOutStream implements ISequentialOutStream {

        private final OutputStream out;

        public SequentialOutStream(@NonNull final OutputStream stream) {
            this.out = stream;
        }

        @Override
        public int write(byte[] data) throws SevenZipException {
            if (data == null || data.length == 0) {
                throw new SevenZipException("null data");
            }
            try {
                out.write(data);
            } catch (IOException e) {
                throw new SevenZipException(e);
            }
            return data.length;
        }

        public void close() throws IOException {
            out.close();
        }
    }
}
