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
     * Method ensures file creation from stream.
     *
     * @param stream - FileOutputStream.
     * @return true if all OK.
     */
    static boolean sync(@NonNull final FileOutputStream stream) {
        try {
            stream.getFD().sync();
            return true;
        } catch (IOException e) {
            LogHelper.e(TAG, "IO Error: ", e);
        }

        return false;
    }

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
     * Get OutputStream from file.
     *
     * @param target The file.
     * @return FileOutputStream.
     */
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
     * Create a file.
     *
     * @param file The file to be created.
     * @return true if creation was successful.
     */
    @SuppressWarnings("RedundantThrows")
    static boolean makeFile(@NonNull final File file) throws IOException {
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
        if (Helper.isAtLeastAPI(LOLLIPOP)) {
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
     * Create a folder.
     *
     * @param file The folder to be created.
     * @return true if creation was successful.
     */
    static boolean makeDir(@NonNull final File file) {
        if (file.exists()) {
            // nothing to create.
            return file.isDirectory();
        }

        // Try the normal way
        if (file.mkdirs()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Helper.isAtLeastAPI(LOLLIPOP)) {
            DocumentFile document = getDocumentFile(file, true);
            // getDocumentFile implicitly creates the directory.
            if (document != null) {
                return document.exists();
            }
        }

        return false;
    }

    /**
     * Delete a file.
     *
     * @param file The file to be deleted.
     * @return true if successfully deleted.
     */
    static boolean deleteFile(@NonNull final File file) {
        // First try the normal deletion
        boolean fileDelete = rmFile(file);
        if (file.delete() || fileDelete) {
            return true;
        }

        // Try with Storage Access Framework
        if (Helper.isAtLeastAPI(LOLLIPOP)) {
            DocumentFile document = getDocumentFile(file, false);
            if (document != null) {
                return document.delete();
            }
        }

        return !file.exists();
    }

    private static boolean rmFile(@NonNull final File folder) {
        boolean totalSuccess = true;
        if (folder.isDirectory()) {
            for (File child : folder.listFiles()) {
                rmFile(child);
            }
            if (!folder.delete()) {
                totalSuccess = false;
            }
        } else {
            if (!folder.delete()) {
                totalSuccess = false;
            }
        }

        return totalSuccess;
    }

    /**
     * Delete a folder.
     *
     * @param folder The folder.
     * @return true if successful.
     */
    static boolean deleteDir(@NonNull final File folder) {
        if (!folder.exists()) {
            return true;
        }
        if (!folder.isDirectory()) {
            return false;
        }
        String[] fileList = folder.list();
        if (fileList != null && fileList.length > 0) {
            //  empty the folder.
            rmDir(folder);
        }
        String[] fileList1 = folder.list();
        if (fileList1 != null && fileList1.length > 0) {
            // Delete only empty folder.
            return false;
        }
        // Try the normal way
        if (folder.delete()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Helper.isAtLeastAPI(LOLLIPOP)) {
            DocumentFile document = getDocumentFile(folder, true);
            if (document != null) {
                return document.delete();
            }
        }

        return !folder.exists();
    }

    private static boolean rmDir(@NonNull final File folder) {
        for (File dir : folder.listFiles()) {
            if (dir.isDirectory()) {
                if (!rmDir(dir)) {
                    return false;
                }
            } else {
                if (!deleteFile(dir)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Copy a file.
     *
     * @param source The source file.
     * @param target The target file.
     * @return true if copying was successful.
     */
    static boolean copyFile(final File source, final File target) {
        final int BUFFER = 10 * 1024;
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
                    // write to output stream
                    byte[] buffer = new byte[BUFFER];
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
     * Rename a folder.
     *
     * @param source The source folder.
     * @param target The target folder.
     * @return true if the renaming was successful.
     */
    static boolean renameDir(@NonNull final File source, @NonNull final File target) {
        // First try the normal rename.
        if (rename(source, target.getName())) {
            return true;
        }
        if (target.exists()) {
            return false;
        }

        // Try the Storage Access Framework if it is just a rename within the same parent folder.
        if (Helper.isAtLeastAPI(LOLLIPOP) && source.getParent().equals(target.getParent())) {
            DocumentFile document = getDocumentFile(source, true);
            if (document != null && document.renameTo(target.getName())) {
                return true;
            }
        }

        // Try the manual way, moving files individually.
        if (!makeDir(target)) {
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

    private static boolean rename(File file, String name) {
        String newName = file.getParent() + "/" + name;
        return !file.getParentFile().canWrite() || file.renameTo(new File(newName));
    }

    /**
     * Move a file.
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
}
