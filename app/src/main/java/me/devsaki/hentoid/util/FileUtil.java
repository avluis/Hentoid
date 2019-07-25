package me.devsaki.hentoid.util;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.HentoidApp;
import timber.log.Timber;

import static android.os.Build.VERSION_CODES.LOLLIPOP;

/**
 * Created by avluis on 08/25/2016.
 * Methods for use by FileHelper
 */
class FileUtil {


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
            Timber.e(e, "IO Error");
        }

        return false;
    }

    /**
     * Get the DocumentFile corresponding to the given file.
     * If the file does not exist, null is returned.
     *
     * @param file        The file.
     * @param isDirectory flag indicating if the given file should be a directory.
     * @return The DocumentFile.
     */
    @Nullable
    static DocumentFile getDocumentFile(@Nonnull final File file, final boolean isDirectory) {
        return getOrCreateDocumentFile(file, isDirectory, false);
    }

    @Nullable
    private static DocumentFile getOrCreateDocumentFile(@Nonnull final File file, boolean isDirectory) {
        return getOrCreateDocumentFile(file, isDirectory, true);
    }

    /**
     * Get the DocumentFile corresponding to the given file.
     * If the file does not exist, null is returned.
     *
     * @param file        The file.
     * @param isDirectory flag indicating if the given file should be a directory.
     * @return The DocumentFile.
     */
    @Nullable
    private static DocumentFile getOrCreateDocumentFile(@Nonnull final File file, boolean isDirectory, boolean canCreate) {
        String baseFolder = FileHelper.getExtSdCardFolder(file);
        boolean returnSDRoot = false;

        // File is from phone memory
        if (baseFolder == null) return DocumentFile.fromFile(file);

        String relativePath = ""; // Path of the file relative to baseFolder
        try {
            String fullPath = file.getCanonicalPath();

            if (!baseFolder.equals(fullPath)) { // Selected file _is_ the base folder
                relativePath = fullPath.substring(baseFolder.length() + 1);
            } else {
                returnSDRoot = true;
            }
        } catch (IOException e) {
            return null;
        } catch (Exception f) {
            returnSDRoot = true;
            //continue
        }

        String sdStorageUriStr = Preferences.getSdStorageUri();
        if (sdStorageUriStr.isEmpty()) return null;

        Uri sdStorageUri = Uri.parse(sdStorageUriStr);

        // Shorten relativePath if part of it is already in sdStorageUri
        String[] uriContents = sdStorageUri.getPath().split(":");
        if (uriContents.length > 1) {
            String relativeUriPath = uriContents[1];
            if (relativePath.equals(relativeUriPath)) {
                relativePath = "";
            } else if (relativePath.startsWith(relativeUriPath)) {
                relativePath = relativePath.substring(relativeUriPath.length() + 1);
            }
        }

        return getOrCreateFromComponents(sdStorageUri, returnSDRoot, relativePath, isDirectory, canCreate);
    }

    /**
     * Get the DocumentFile corresponding to the given elements.
     * If it does not exist, it is created.
     *
     * @param rootURI      Uri representing root
     * @param returnRoot   True if method has just to return the DocumentFile representing the given root
     * @param relativePath Relative path to the Document to be found/created (relative to given root)
     * @param isDirectory  True if the given elements are supposed to be a directory; false if they are supposed to be a file
     * @param canCreate    Behaviour when not found : True => creates a new file/folder / False => returns null
     * @return DocumentFile corresponding to the given file.
     */
    @Nullable
    private static DocumentFile getOrCreateFromComponents(@Nonnull Uri rootURI, boolean returnRoot,
                                                          String relativePath, boolean isDirectory,
                                                          boolean canCreate) {
        // start with root and then parse through document tree.
        Context context = HentoidApp.getAppContext();
        DocumentFile document = DocumentFile.fromTreeUri(context, rootURI);

        if (null == document) return null;
        if (returnRoot || null == relativePath || relativePath.isEmpty()) return document;

        String[] parts = relativePath.split(File.separator);
        for (int i = 0; i < parts.length; i++) {
            DocumentFile nextDocument = document.findFile(parts[i]);
            // The folder might exist in its capitalized version (might happen with legacy installs from the FakkuDroid era)
            if (null == nextDocument)
                nextDocument = document.findFile(Helper.capitalizeString(parts[i]));

            // The folder definitely doesn't exist at all
            if (null == nextDocument) {
                if (canCreate) {
                    Timber.d("Document %s - part #%s : '%s' not found; creating", document.getName(), String.valueOf(i), parts[i]);

                    if ((i < parts.length - 1) || isDirectory) {
                        nextDocument = document.createDirectory(parts[i]);
                        if (null == nextDocument) {
                            Timber.e("Failed to create subdirectory %s/%s", document.getName(), parts[i]);
                        }
                    } else {
                        nextDocument = document.createFile("image", parts[i]);
                        if (null == nextDocument) {
                            Timber.e("Failed to create file %s/image%s", document.getName(), parts[i]);
                        }
                    }
                } else {
                    return null;
                }
            }
            document = nextDocument;
            if (null == document) break;
        }

        return document;
    }

    /**
     * Get OutputStream from file.
     *
     * @param target The file.
     * @return FileOutputStream.
     */
    static OutputStream getOutputStream(@NonNull final File target) throws IOException {
        try {
            return FileUtils.openOutputStream(target);
        } catch (IOException e) {
            Timber.d("Could not open file (expected)");
        }
        try {
            if (Build.VERSION.SDK_INT >= LOLLIPOP) {
                // Storage Access Framework
                DocumentFile targetDocument = getOrCreateDocumentFile(target, false);
                if (targetDocument != null) {
                    Context context = HentoidApp.getAppContext();
                    return context.getContentResolver().openOutputStream(
                            targetDocument.getUri());
                }
            }
        } catch (Exception e) {
            Timber.e(e, "Error [%s] while attempting to get file: %s ", e.getMessage(), target.getAbsolutePath());
            throw new IOException(e);
        }

        throw new IOException("Error while attempting to get file : " + target.getAbsolutePath());
    }

    static OutputStream getOutputStream(@NonNull final DocumentFile target) throws FileNotFoundException {
        Context context = HentoidApp.getAppContext();
        return context.getContentResolver().openOutputStream(target.getUri());
    }

    static InputStream getInputStream(@NonNull final File target) throws IOException {
        try {
            return FileUtils.openInputStream(target);
        } catch (IOException e) {
            Timber.d("Could not open file (expected)");
        }

        try {
            if (Build.VERSION.SDK_INT >= LOLLIPOP) {
                // Storage Access Framework
                DocumentFile targetDocument = getOrCreateDocumentFile(target, false);
                if (targetDocument != null) {
                    Context context = HentoidApp.getAppContext();
                    return context.getContentResolver().openInputStream(
                            targetDocument.getUri());
                }
            }
            throw new IOException("Error while attempting to get file : " + target.getAbsolutePath());
        } catch (Exception e) {
            Timber.e(e, "Error while attempting to get file: %s", target.getAbsolutePath());
            throw new IOException(e);
        }
    }


    /**
     * Create a file.
     *
     * @param file The file to be created.
     * @return true if creation was successful.
     */
    static boolean makeFile(@NonNull final File file) {
        if (file.exists()) {
            // nothing to create.
            return !file.isDirectory();
        }

        // Try the normal way
        try {
            return file.createNewFile();
        } catch (IOException e) {
            // Fail silently
        }

        // Try with Storage Access Framework.
        if (Build.VERSION.SDK_INT >= LOLLIPOP) {
            DocumentFile document = getOrCreateDocumentFile(file.getParentFile(), true);
            // getOrCreateDocumentFile implicitly creates the directory.
            try {
                if (document != null) {
                    //MimeTypes.getMimeType(file)
                    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileHelper.getExtension(file.getName()));
                    if (null == mimeType) mimeType = "application/octet-stream";

                    return document.createFile(mimeType, file.getName()) != null;
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
     * @return true if creation was successful or the folder already exists
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
        if (Build.VERSION.SDK_INT >= LOLLIPOP) {
            DocumentFile document = getOrCreateDocumentFile(file, true);
            // getOrCreateDocumentFile implicitly creates the directory.
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
     * @return true if successfully deleted or if the file does not exist.
     */
    static boolean deleteFile(@NonNull final File file) {
        return !file.exists() || FileUtils.deleteQuietly(file) || deleteWithSAF(file);
    }

    static boolean deleteWithSAF(File file) {
        if (Build.VERSION.SDK_INT >= LOLLIPOP) {
            DocumentFile document = getOrCreateDocumentFile(file, true);
            if (document != null) {
                return document.delete();
            }
        }

        return false;
    }

    static boolean renameWithSAF(File srcDir, String newName) {
        if (Build.VERSION.SDK_INT >= LOLLIPOP) {
            DocumentFile srcDocument = getOrCreateDocumentFile(srcDir, true);
            if (srcDocument != null) return srcDocument.renameTo(newName);
        }
        return false;
    }
}
