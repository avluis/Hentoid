package me.devsaki.hentoid.util;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

import me.devsaki.hentoid.HentoidApp;

/**
 * Created by avluis on 08/25/2016.
 * Methods for use by FileHelper
 */
class FileUtil {
    private static final String TAG = LogHelper.makeLogTag(FileUtil.class);

    private static final int LOLLIPOP = Build.VERSION_CODES.LOLLIPOP;

    /**
     * Get a DocumentFile corresponding to the given file.
     * If the file does not exist, it is created.
     *
     * @param file        The file.
     * @param isDirectory flag indicating if the file should be a directory.
     * @return The DocumentFile.
     */
    private static DocumentFile getDocumentFile(final File file, final boolean isDirectory) {
        String baseFolder = FileHelper.getExtSdCardFolder(file);
        boolean originalDirectory = false;
        if (baseFolder == null) {
            return null;
        }

        String relativePath = null;
        try {
            String fullPath = file.getCanonicalPath();
            if (!baseFolder.equals(fullPath)) {
                relativePath = fullPath.substring(baseFolder.length() + 1);
            } else {
                originalDirectory = true;
            }
        } catch (IOException e) {
            return null;
        } catch (Exception f) {
            originalDirectory = true;
            //continue
        }

        String as = FileHelper.getStringUri();
        Uri treeUri = null;
        if (as != null) {
            treeUri = Uri.parse(as);
        }
        if (treeUri == null) {
            return null;
        }

        return documentFileHelper(treeUri, originalDirectory, relativePath, isDirectory);
    }

    private static DocumentFile documentFileHelper(Uri treeUri, boolean originalDirectory,
                                                   String relativePath, boolean isDirectory) {
        // start with root of SD card and then parse through document tree.
        Context cxt = HentoidApp.getAppContext();
        DocumentFile document = DocumentFile.fromTreeUri(cxt, treeUri);
        if (originalDirectory) {
            return document;
        }
        String[] parts = relativePath.split("/");
        for (int i = 0; i < parts.length; i++) {
            DocumentFile nextDocument = document.findFile(parts[i]);
            if (nextDocument == null) {
                if ((i < parts.length - 1) || isDirectory) {
                    nextDocument = document.createDirectory(parts[i]);
                } else {
                    nextDocument = document.createFile("image", parts[i]);
                }
            }
            document = nextDocument;
        }

        return document;
    }

    /**
     * Copy a file.
     *
     * @param source The source file.
     * @param target The target file.
     * @return true if copying was successful.
     */
    static boolean copyFile(final File source, final File target) {
        FileInputStream inStream = null;
        OutputStream outStream = null;
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        try {
            inStream = new FileInputStream(source);
            // First try the normal way
            if (FileHelper.isWritable(target)) {
                // standard way
                outStream = new FileOutputStream(target);
                inChannel = inStream.getChannel();
                outChannel = ((FileOutputStream) outStream).getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
            } else {
                if (Helper.isAtLeastAPI(LOLLIPOP)) {
                    // Storage Access Framework
                    DocumentFile targetDocument = getDocumentFile(target, false);
                    if (targetDocument != null) {
                        Context cxt = HentoidApp.getAppContext();
                        outStream = cxt.getContentResolver().openOutputStream(
                                targetDocument.getUri());
                    }
                } else {
                    return false;
                }

                if (outStream != null) {
                    // Both for SAF and for Kitkat, write to output stream.
                    byte[] buffer = new byte[16384]; // MAGIC_NUMBER
                    int bytesRead;
                    while ((bytesRead = inStream.read(buffer)) != -1) {
                        outStream.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (Exception e) {
            LogHelper.e(TAG, "Error while copying file from " + source.getAbsolutePath() + " to "
                    + target.getAbsolutePath() + ": ", e);

            return false;
        } finally {
            try {
                if (inStream != null) {
                    inStream.close();
                }
                if (outStream != null) {
                    outStream.close();
                }
                if (inChannel != null) {
                    inChannel.close();
                }
                if (outChannel != null) {
                    outChannel.close();
                }
            } catch (Exception e) {
                // ignore exception
            }
        }

        return true;
    }

    /**
     * Delete a file.
     * May be even on external SD card.
     *
     * @param file The file to be deleted.
     * @return true if successfully deleted.
     */
    static boolean deleteFile(@NonNull final File file) {
        // First try the normal deletion
        boolean fileDelete = deleteFilesInFolder(file);
        if (file.delete() || fileDelete) {
            return true;
        }

        // Try with Storage Access Framework
        if (Helper.isAtLeastAPI(LOLLIPOP) && FileHelper.isOnExtSdCard(file)) {
            DocumentFile document = getDocumentFile(file, false);
            if (document != null) {
                return document.delete();
            }
        }

        return !file.exists();
    }

    /**
     * Delete all files in a folder.
     *
     * @param folder The folder.
     * @return true if successful.
     */
    private static boolean deleteFilesInFolder(@NonNull final File folder) {
        boolean totalSuccess = true;
        if (folder.isDirectory()) {
            for (File child : folder.listFiles()) {
                deleteFilesInFolder(child);
            }
            if (!folder.delete())
                totalSuccess = false;
        } else {
            if (!folder.delete())
                totalSuccess = false;
        }

        return totalSuccess;
    }

    /**
     * Method ensures about file creation from stream.
     * For Samsung like devices
     *
     * @param stream - OutputStream
     * @return true if all OK.
     */
    static boolean sync(@NonNull final OutputStream stream) {
        return (stream instanceof FileOutputStream) && sync((FileOutputStream) stream);
    }

    /**
     * Method ensures about file creation from stream.
     * For Samsung like devices
     *
     * @param stream - FileOutputStream
     * @return true if all OK.
     */
    static boolean sync(@NonNull final FileOutputStream stream) {
        try {
            stream.getFD().sync();
            return true;
        } catch (IOException e) {
            LogHelper.e(TAG, "IO Error: " + e);
        }

        return false;
    }

    static OutputStream getOutputStream(@NonNull final File target) {
        OutputStream outStream = null;
        try {
            // First try the normal way
            if (FileHelper.isWritable(target)) {
                // standard way
                outStream = new FileOutputStream(target);
            } else {
                if (Helper.isAtLeastAPI(LOLLIPOP)) {
                    // Storage Access Framework
                    DocumentFile targetDocument = getDocumentFile(target, false);
                    if (targetDocument != null) {
                        Context cxt = HentoidApp.getAppContext();
                        outStream = cxt.getContentResolver().openOutputStream(
                                targetDocument.getUri());
                    }
                }
            }
        } catch (Exception e) {
            LogHelper.e(TAG, "Error while attempting to get file: " + target.getAbsolutePath(), e);
        }

        return outStream;
    }

    /**
     * Move a file.
     * The target file may even be on external SD card.
     *
     * @param source The source file.
     * @param target The target file.
     * @return true if the copying was successful.
     */
    static boolean moveFile(@NonNull final File source, @NonNull final File target) {
        // First try the normal rename
        if (source.renameTo(target)) {
            return true;
        }

        boolean success = copyFile(source, target);
        if (success) {
            success = deleteFile(source);
        }

        return success;
    }

    /**
     * Rename a folder.
     * In case of extSdCard in Kitkat, the old folder stays in place, but files are moved.
     *
     * @param source The source folder.
     * @param target The target folder.
     * @return true if the renaming was successful.
     */
    static boolean renameFolder(@NonNull final File source, @NonNull final File target) {
        // First try the normal rename.
        if (rename(source, target.getName())) {
            return true;
        }
        if (target.exists()) {
            return false;
        }

        // Try the Storage Access Framework if it is just a rename within the same parent folder.
        if (Helper.isAtLeastAPI(LOLLIPOP) && source.getParent().equals(target.getParent()) &&
                FileHelper.isOnExtSdCard(source)) {
            DocumentFile document = getDocumentFile(source, true);
            if (document != null && document.renameTo(target.getName())) {
                return true;
            }
        }

        // Try the manual way, moving files individually.
        if (!mkDir(target)) {
            return false;
        }

        File[] sourceFiles = source.listFiles();
        if (sourceFiles == null) {
            return true;
        }

        for (File sourceFile : sourceFiles) {
            String fileName = sourceFile.getName();
            File targetFile = new File(target, fileName);
            if (!copyFile(sourceFile, targetFile)) {
                // stop on first error
                return false;
            }
        }
        // Only after successfully copying all files, delete files on source folder.
        for (File sourceFile : sourceFiles) {
            if (!deleteFile(sourceFile)) {
                // stop on first error
                return false;
            }
        }

        return true;
    }

    private static boolean rename(File f, String name) {
        String newName = f.getParent() + "/" + name;
        return !f.getParentFile().canWrite() || f.renameTo(new File(newName));
    }

    /**
     * Create a folder.
     *
     * @param file The folder to be created.
     * @return true if creation was successful.
     */
    static boolean mkDir(@NonNull final File file) {
        if (file.exists()) {
            // nothing to create.
            return file.isDirectory();
        }

        // Try the normal way
        if (file.mkdirs()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Helper.isAtLeastAPI(LOLLIPOP) && FileHelper.isOnExtSdCard(file)) {
            DocumentFile document = getDocumentFile(file, true);
            // getDocumentFile implicitly creates the directory.
            if (document != null) {
                return document.exists();
            }
        }

        return false;
    }

    /**
     * Create a file.
     *
     * @param file The file to be created.
     * @return true if creation was successful.
     */
    @SuppressWarnings("RedundantThrows")
    static boolean mkFile(@NonNull final File file) throws IOException {
        if (file.exists()) {
            // nothing to create.
            return !file.isDirectory();
        }

        // Try the normal way
        try {
            if (file.createNewFile()) {
                return true;
            }
        } catch (IOException e) {
            // Fail silently
        }
        // Try with Storage Access Framework.
        if (Helper.isAtLeastAPI(LOLLIPOP) && FileHelper.isOnExtSdCard(file)) {
            DocumentFile document = getDocumentFile(file.getParentFile(), true);
            // getDocumentFile implicitly creates the directory.
            try {
                if (document != null) {
                    return document.createFile(
                            MimeTypes.getMimeType(file), file.getName()) != null;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Delete a folder.
     *
     * @param file The folder name.
     * @return true if successful.
     */
    static boolean rmDir(@NonNull final File file) {
        if (!file.exists()) {
            return true;
        }
        if (!file.isDirectory()) {
            return false;
        }
        String[] fileList = file.list();
        if (fileList != null && fileList.length > 0) {
            //  empty the folder.
            rmDirHelper(file);
        }
        String[] fileList1 = file.list();
        if (fileList1 != null && fileList1.length > 0) {
            // Delete only empty folder.
            return false;
        }
        // Try the normal way
        if (file.delete()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Helper.isAtLeastAPI(LOLLIPOP)) {
            DocumentFile document = getDocumentFile(file, true);
            if (document != null) {
                return document.delete();
            }
        }

        return !file.exists();
    }

    private static boolean rmDirHelper(@NonNull final File file) {
        for (File file1 : file.listFiles()) {
            if (file1.isDirectory()) {
                if (!rmDirHelper(file1)) {
                    return false;
                }
            } else {
                if (!deleteFile(file1)) {
                    return false;
                }
            }
        }

        return true;
    }
}
