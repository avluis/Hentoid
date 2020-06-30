package me.devsaki.hentoid.util;

import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Stream;
import com.crashlytics.android.Crashlytics;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.threeten.bp.Instant;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImageViewerActivity;
import me.devsaki.hentoid.activities.UnlockActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.json.JsonContentCollection;
import timber.log.Timber;

import static com.annimon.stream.Collectors.toList;

/**
 * Utility class for Content-related operations
 */
public final class ContentHelper {

    private static final String UNAUTHORIZED_CHARS = "[^a-zA-Z0-9.-]";

    // TODO empty this cache at some point
    private static final Map<String, String> fileNameMatchCache = new HashMap<>();


    private ContentHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Open the app's web browser to view the given Content's gallery page
     *
     * @param context Context to use for the action
     * @param content Content to view
     */
    public static void viewContentGalleryPage(@NonNull final Context context, @NonNull Content content) {
        viewContentGalleryPage(context, content, false);
    }

    /**
     * Open the app's web browser to view the given Content's gallery page
     *
     * @param context Context to use for the action
     * @param content Content to view
     * @param wrapPin True if the intent should be wrapped with PIN protection
     */
    public static void viewContentGalleryPage(@NonNull final Context context, @NonNull Content content, boolean wrapPin) {
        if (content.getSite().equals(Site.NONE)) return;

        Intent intent = new Intent(context, content.getWebActivityClass());
        BaseWebActivityBundle.Builder builder = new BaseWebActivityBundle.Builder();
        builder.setUrl(content.getGalleryUrl());
        intent.putExtras(builder.getBundle());
        if (wrapPin) intent = UnlockActivity.wrapIntent(context, intent);
        context.startActivity(intent);
    }

    /**
     * Update the given Content's JSON file with its current values
     *
     * @param context Context to use for the action
     * @param content Content whose JSON file to update
     */
    public static void updateContentJson(@NonNull Context context, @NonNull Content content) {
        Helper.assertNonUiThread();
        DocumentFile file = DocumentFile.fromSingleUri(context, Uri.parse(content.getJsonUri()));
        if (null == file)
            throw new InvalidParameterException("'" + content.getJsonUri() + "' does not refer to a valid file");

        try {
            JsonHelper.updateJson(context, JsonContent.fromEntity(content), JsonContent.class, file);
        } catch (IOException e) {
            Timber.e(e, "Error while writing to %s", content.getJsonUri());
        }
    }

    /**
     * Create the given Content's JSON file and populate it with its current values
     *
     * @param content Content whose JSON file to create
     */
    public static void createContentJson(@NonNull Context context, @NonNull Content content) {
        Helper.assertNonUiThread();
        DocumentFile folder = DocumentFile.fromTreeUri(context, Uri.parse(content.getStorageUri()));
        if (null == folder || !folder.exists()) return;
        try {
            JsonHelper.jsonToFile(context, JsonContent.fromEntity(content), JsonContent.class, folder);
        } catch (IOException e) {
            Timber.e(e, "Error while writing to %s", content.getStorageUri());
        }
    }

    public static boolean updateQueueJson(@NonNull Context context, @NonNull CollectionDAO dao) {
        List<QueueRecord> queue = dao.selectQueue();
        // Save current queue (to be able to restore it in case the app gets uninstalled)
        List<Content> queuedContent = Stream.of(queue).map(qr -> qr.content.getTarget()).withoutNulls().toList();
        JsonContentCollection contentCollection = new JsonContentCollection();
        contentCollection.setQueue(queuedContent);

        DocumentFile rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(Preferences.getStorageUri()));
        if (null == rootFolder || !rootFolder.exists()) return false;

        try {
            JsonHelper.jsonToFile(context, contentCollection, JsonContentCollection.class, rootFolder, Consts.QUEUE_JSON_FILE_NAME);
        } catch (IOException | IllegalArgumentException e) {
            // NB : IllegalArgumentException might happen for an unknown reason on certain devices
            // even though all the file existence checks are in place
            // ("Failed to determine if primary:.Hentoid/queue.json is child of primary:.Hentoid: java.io.FileNotFoundException: Missing file for primary:.Hentoid/queue.json at /storage/emulated/0/.Hentoid/queue.json")
            Timber.e(e);
            Crashlytics.logException(e);
            return false;
        }
        return true;
    }

    /**
     * Open the given Content in the built-in image viewer
     *
     * @param context      Context to use for the action
     * @param content      Content to view
     * @param searchParams Current search parameters (so that the next/previous book feature
     *                     is faithful to the library screen's order)
     */
    public static boolean openHentoidViewer(@NonNull Context context, @NonNull Content content, Bundle searchParams) {
        // Check if the book has at least its own folder
        if (content.getStorageUri().isEmpty()) return false;

        Timber.d("Opening: %s from: %s", content.getTitle(), content.getStorageUri());

        ImageViewerActivityBundle.Builder builder = new ImageViewerActivityBundle.Builder();
        builder.setContentId(content.getId());
        if (searchParams != null) builder.setSearchParams(searchParams);

        Intent viewer = new Intent(context, ImageViewerActivity.class);
        viewer.putExtras(builder.getBundle());

        context.startActivity(viewer);
        return true;
    }

    /**
     * Update the given Content's number of reads in both DB and JSON file
     *
     * @param context Context to use for the action
     * @param dao     DAO to use for the action
     * @param content Content to update
     */
    public static void updateContentReads(@NonNull Context context, @Nonnull CollectionDAO dao, @NonNull Content content) {
        content.increaseReads().setLastReadDate(Instant.now().toEpochMilli());
        dao.insertContent(content);

        if (!content.getJsonUri().isEmpty()) updateContentJson(context, content);
        else createContentJson(context, content);
    }

    /**
     * Find the picture files for the given Content
     * NB1 : Pictures with non-supported formats are not included in the results
     * NB2 : Cover picture is not included in the results
     *
     * @param content Content to retrieve picture files for
     * @return List of picture files
     */
    public static List<DocumentFile> getPictureFilesFromContent(@NonNull final Context context, @NonNull final Content content) {
        Helper.assertNonUiThread();
        String storageUri = content.getStorageUri();

        Timber.d("Opening: %s from: %s", content.getTitle(), storageUri);
        DocumentFile folder = DocumentFile.fromTreeUri(context, Uri.parse(storageUri));
        if (null == folder || !folder.exists()) {
            Timber.d("File not found!! Exiting method.");
            return new ArrayList<>();
        }

        return FileHelper.listFoldersFilter(context,
                folder,
                displayName -> (displayName.toLowerCase().startsWith(Consts.THUMB_FILE_NAME)
                        && Helper.isImageExtensionSupported(FileHelper.getExtension(displayName))
                )
        );
    }

    /**
     * Remove the given Content from the disk and the DB
     *
     * @param content Content to be removed
     * @param dao     DAO to be used
     */
    public static void removeContent(@NonNull Context context, @NonNull Content content, @NonNull CollectionDAO dao) {
        Helper.assertNonUiThread();
        // Remove from DB
        // NB : start with DB to have a LiveData feedback, because file removal can take much time
        dao.deleteContent(content);
        // If the book has just starting being downloaded and there are no complete pictures on memory yet, it has no storage folder => nothing to delete
        if (!content.getStorageUri().isEmpty()) {
            DocumentFile folder = DocumentFile.fromTreeUri(context, Uri.parse(content.getStorageUri()));
            if (null == folder || !folder.exists()) return;

            if (folder.delete()) {
                Timber.i("Directory removed : %s", content.getStorageUri());
            } else {
                Timber.w("Failed to delete directory : %s", content.getStorageUri()); // TODO use exception to display feedback on screen
            }
        }
    }

    /**
     * Remove the given page from the disk and the DB
     *
     * @param image Page to be removed
     * @param dao   DAO to be used
     */
    public static void removePage(@NonNull ImageFile image, @NonNull CollectionDAO dao, @NonNull final Context context) {
        Helper.assertNonUiThread();
        // Remove from DB
        // NB : start with DB to have a LiveData feedback, because file removal can take much time
        dao.deleteImageFile(image);

        // Remove the page from disk
        if (image.getFileUri() != null && !image.getFileUri().isEmpty()) {
            Uri uri = Uri.parse(image.getFileUri());

            DocumentFile doc = DocumentFile.fromSingleUri(context, uri);
            if (doc != null && doc.exists()) doc.delete();
        }

        // Update content JSON if it exists (i.e. if book is not queued)
        Content content = dao.selectContent(image.content.getTargetId());
        if (content != null && !content.getJsonUri().isEmpty()) updateContentJson(context, content);
    }

    /**
     * Create the download directory of the given content
     *
     * @param context Context
     * @param content Content for which the directory to create
     * @return Created directory
     */
    @Nullable
    public static DocumentFile createContentDownloadDir(@NonNull Context context, @NonNull Content content) {
        DocumentFile siteDownloadDir = getOrCreateSiteDownloadDir(context, null, content.getSite());
        if (null == siteDownloadDir) return null;

        String bookFolderName = formatBookFolderName(content);
        DocumentFile bookFolder = FileHelper.findFolder(context, siteDownloadDir, bookFolderName);
        if (null == bookFolder) { // Create
            return siteDownloadDir.createDirectory(bookFolderName);
        } else return bookFolder;
    }

    /**
     * Format the download directory path of the given content according to current user preferences
     *
     * @param content Content to get the path from
     * @return Canonical download directory path of the given content, according to current user preferences
     */
    public static String formatBookFolderName(@NonNull final Content content) {
        String result = "";

        String title = content.getTitle().replaceAll(UNAUTHORIZED_CHARS, "_");
        String author = content.getAuthor().toLowerCase().replaceAll(UNAUTHORIZED_CHARS, "_");

        switch (Preferences.getFolderNameFormat()) {
            case Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_TITLE_ID:
                result += title;
                break;
            case Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID:
                result += author + " - " + title;
                break;
            case Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_TITLE_AUTH_ID:
                result += title + " - " + author;
                break;
        }
        result += " - ";

        // Unique content ID
        String suffix = formatBookId(content);

        // Truncate folder dir to something manageable for Windows
        // If we are to assume NTFS and Windows, then the fully qualified file, with it's drivename, path, filename, and extension, altogether is limited to 260 characters.
        int truncLength = Preferences.getFolderTruncationNbChars();
        int titleLength = result.length();
        if (truncLength > 0 && titleLength + suffix.length() > truncLength)
            result = result.substring(0, truncLength - suffix.length() - 1);

        result += suffix;

        return result;
    }

    /**
     * Format the Content ID for folder naming purposes
     *
     * @param content Content whose ID to format
     * @return Formatted Content ID
     */
    @SuppressWarnings("squid:S2676") // Math.abs is used for formatting purposes only
    public static String formatBookId(@NonNull final Content content) {
        String id = content.getUniqueSiteId();
        // For certain sources (8muses, fakku), unique IDs are strings that may be very long
        // => shorten them by using their hashCode
        if (id.length() > 10) id = Helper.formatIntAsStr(Math.abs(id.hashCode()), 10);
        return "[" + id + "]";
    }

    /**
     * Return the given site's download directory. Create it if it doesn't exist.
     *
     * @param context Context to use for the action
     * @param site    Site to get the download directory for
     * @return Download directory of the given Site
     */
    @Nullable
    static DocumentFile getOrCreateSiteDownloadDir(@NonNull Context context, @Nullable ContentProviderClient client, @NonNull Site site) {
        String appUriStr = Preferences.getStorageUri();
        if (appUriStr.isEmpty()) {
            Timber.e("No storage URI defined for the app");
            return null;
        }

        DocumentFile appFolder = DocumentFile.fromTreeUri(context, Uri.parse(appUriStr));
        if (null == appFolder || !appFolder.exists()) {
            Timber.e("App folder %s does not exist", appUriStr);
            return null;
        }

        String siteFolderName = site.getFolder();
        DocumentFile siteFolder;
        if (null == client)
            siteFolder = FileHelper.findFolder(context, appFolder, siteFolderName);
        else
            siteFolder = FileHelper.findFolder(context, appFolder, client, siteFolderName);

        if (null == siteFolder) // Create
            return appFolder.createDirectory(siteFolderName);
        else return siteFolder;
    }

    /**
     * Open the "share with..." Android dialog for the given Content
     *
     * @param context Context to use for the action
     * @param item    Content to share
     */
    public static void shareContent(@NonNull final Context context, @NonNull final Content item) {
        String url = item.getGalleryUrl();

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, item.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, url);

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_to)));
    }

    private static String removeLeadingZeroesAndExtension(String s) {
        if (null == s) return "";

        int beginIndex = 0;
        if (s.startsWith("0")) beginIndex = -1;

        for (int i = 0; i < s.length(); i++) {
            if (-1 == beginIndex && s.charAt(i) != '0') beginIndex = i;
            if ('.' == s.charAt(i)) return s.substring(beginIndex, i);
        }

        return (-1 == beginIndex) ? "0" : s.substring(beginIndex);
    }

    private static String removeLeadingZeroesAndExtensionCached(String s) {
        if (fileNameMatchCache.containsKey(s)) return fileNameMatchCache.get(s);
        else {
            String result = removeLeadingZeroesAndExtension(s);
            fileNameMatchCache.put(s, result);
            return result;
        }
    }

    public static List<ImageFile> matchFilesToImageList(@NonNull final List<DocumentFile> files, @NonNull final List<ImageFile> images) {
        Map<String, ImmutablePair<String, Long>> fileNameProperties = new HashMap<>(files.size());
        List<ImageFile> result = new ArrayList<>();

        for (DocumentFile file : files)
            fileNameProperties.put(removeLeadingZeroesAndExtensionCached(file.getName()), new ImmutablePair<>(file.getUri().toString(), file.length()));

        for (ImageFile img : images) {
            String imgName = removeLeadingZeroesAndExtensionCached(img.getName());
            if (fileNameProperties.containsKey(imgName)) {
                ImmutablePair<String, Long> property = fileNameProperties.get(imgName);
                if (property != null)
                    result.add(img.setFileUri(property.left).setSize(property.right).setStatus(StatusContent.DOWNLOADED).setIsCover(imgName.equals(Consts.THUMB_FILE_NAME)));
            } else
                Timber.i(">> img dropped %s", imgName);
        }
        return result;
    }

    public static List<ImageFile> createImageListFromFolder(@NonNull final Context context, @NonNull final DocumentFile folder) {
        List<DocumentFile> imageFiles = FileHelper.listFiles(context, folder, Helper.getImageNamesFilter());
        if (!imageFiles.isEmpty())
            return createImageListFromFiles(imageFiles);
        else return Collections.emptyList();
    }

    public static List<ImageFile> createImageListFromFiles(@NonNull final List<DocumentFile> files) {
        return createImageListFromFiles(files, StatusContent.DOWNLOADED, 0, "");
    }

    public static List<ImageFile> createImageListFromFiles(@NonNull final List<DocumentFile> files, StatusContent status, int initialOrder, String namePrefix) {
        Helper.assertNonUiThread();
        List<ImageFile> result = new ArrayList<>();
        int order = initialOrder;
        // Sort files by name alpha
        List<DocumentFile> fileList = Stream.of(files).withoutNulls().sortBy(DocumentFile::getName).collect(toList());
        for (DocumentFile f : fileList) {
            String name = namePrefix + ((f.getName() != null) ? f.getName() : "");
            ImageFile img = new ImageFile();
            if (name.startsWith(Consts.THUMB_FILE_NAME)) img.setIsCover(true);
            else order++;
            img.setName(FileHelper.getFileNameWithoutExtension(name)).setOrder(order).setUrl("").setStatus(status).setFileUri(f.getUri().toString()).setSize(f.length());
            img.setMimeType(FileHelper.getMimeTypeFromExtension(FileHelper.getExtension(name)));
            result.add(img);
        }
        return result;
    }

    public static Map<String, String> parseDownloadParams(final String downloadParamsStr) {
        // Handle empty and {}
        if (null == downloadParamsStr || downloadParamsStr.trim().length() <= 2)
            return new HashMap<>();

        try {
            return JsonHelper.jsonToObject(downloadParamsStr, JsonHelper.MAP_STRINGS);
        } catch (IOException e) {
            Timber.w(e);
        }
        return new HashMap<>();
    }
}
