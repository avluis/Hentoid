package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.threeten.bp.Instant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImageViewerActivity;
import me.devsaki.hentoid.activities.UnlockActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.util.exception.FileNotRemovedException;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pl.droidsonroids.jspoon.HtmlAdapter;
import pl.droidsonroids.jspoon.Jspoon;
import timber.log.Timber;

import static com.annimon.stream.Collectors.toList;
import static me.devsaki.hentoid.util.network.HttpHelper.HEADER_CONTENT_TYPE;

/**
 * Utility class for Content-related operations
 */
public final class ContentHelper {

    private static final String UNAUTHORIZED_CHARS = "[^a-zA-Z0-9.-]";
    private static final int[] libraryStatus = new int[]{StatusContent.DOWNLOADED.getCode(), StatusContent.MIGRATED.getCode(), StatusContent.EXTERNAL.getCode()};
    private static final int[] queueStatus = new int[]{StatusContent.DOWNLOADING.getCode(), StatusContent.PAUSED.getCode(), StatusContent.ERROR.getCode()};
    private static final int[] queueTabStatus = new int[]{StatusContent.DOWNLOADING.getCode(), StatusContent.PAUSED.getCode()};

    // TODO empty this cache at some point
    private static final Map<String, String> fileNameMatchCache = new HashMap<>();


    private ContentHelper() {
        throw new IllegalStateException("Utility class");
    }


    public static int[] getLibraryStatuses() {
        return libraryStatus;
    }

    public static boolean isInLibrary(@NonNull final StatusContent status) {
        return Helper.getListFromPrimitiveArray(libraryStatus).contains(status.getCode());
    }

    public static int[] getQueueStatuses() {
        return queueStatus;
    }

    public static boolean isInQueue(@NonNull final StatusContent status) {
        return Helper.getListFromPrimitiveArray(queueStatus).contains(status.getCode());
    }

    private static boolean isInQueueTab(@NonNull final StatusContent status) {
        return Helper.getListFromPrimitiveArray(queueTabStatus).contains(status.getCode());
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

        Intent intent = new Intent(context, Content.getWebActivityClass(content.getSite()));
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

        DocumentFile file = FileHelper.getFileFromSingleUriString(context, content.getJsonUri());
        if (null == file)
            throw new IllegalArgumentException("'" + content.getJsonUri() + "' does not refer to a valid file");

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
        if (content.isArchive()) return; // Keep that as is, we can't find the parent folder anyway

        DocumentFile folder = FileHelper.getFolderFromTreeUriString(context, content.getStorageUri());
        if (null == folder) return;
        try {
            JsonHelper.jsonToFile(context, JsonContent.fromEntity(content), JsonContent.class, folder);
        } catch (IOException e) {
            Timber.e(e, "Error while writing to %s", content.getStorageUri());
        }
    }

    /**
     * Update the JSON file that stores the queue with the current contents of the queue
     *
     * @param context Context to be used
     * @param dao     DAO to be used
     * @return True if the queue JSON file has been updated properly; false instead
     */
    public static boolean updateQueueJson(@NonNull Context context, @NonNull CollectionDAO dao) {
        Helper.assertNonUiThread();
        List<QueueRecord> queue = dao.selectQueue();
        List<Content> errors = dao.selectErrorContentList();

        // Save current queue (to be able to restore it in case the app gets uninstalled)
        List<Content> queuedContent = Stream.of(queue).map(qr -> qr.getContent().getTarget()).withoutNulls().toList();
        if (errors != null) queuedContent.addAll(errors);

        JsonContentCollection contentCollection = new JsonContentCollection();
        contentCollection.setQueue(queuedContent);

        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(context, Preferences.getStorageUri());
        if (null == rootFolder) return false;

        try {
            JsonHelper.jsonToFile(context, contentCollection, JsonContentCollection.class, rootFolder, Consts.QUEUE_JSON_FILE_NAME);
        } catch (IOException | IllegalArgumentException e) {
            // NB : IllegalArgumentException might happen for an unknown reason on certain devices
            // even though all the file existence checks are in place
            // ("Failed to determine if primary:.Hentoid/queue.json is child of primary:.Hentoid: java.io.FileNotFoundException: Missing file for primary:.Hentoid/queue.json at /storage/emulated/0/.Hentoid/queue.json")
            Timber.e(e);
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.recordException(e);
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
    public static void updateContentReadStats(
            @NonNull Context context,
            @Nonnull CollectionDAO dao,
            @NonNull Content content,
            @NonNull List<ImageFile> images,
            int targetLastReadPageIndex,
            boolean updateReads) {
        content.setLastReadPageIndex(targetLastReadPageIndex);
        if (updateReads) content.increaseReads().setLastReadDate(Instant.now().toEpochMilli());
        dao.replaceImageList(content.getId(), images);
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
        DocumentFile folder = FileHelper.getFolderFromTreeUriString(context, storageUri);
        if (null == folder) {
            Timber.d("File not found!! Exiting method.");
            return new ArrayList<>();
        }

        return FileHelper.listFoldersFilter(context,
                folder,
                displayName -> (displayName.toLowerCase().startsWith(Consts.THUMB_FILE_NAME)
                        && ImageHelper.isImageExtensionSupported(FileHelper.getExtension(displayName))
                )
        );
    }

    /**
     * Remove the given Content from the disk and the DB
     *
     * @param context Context to be used
     * @param dao     DAO to be used
     * @param content Content to be removed
     * @throws ContentNotRemovedException in case an issue prevents the content from being actually removed
     */
    public static void removeContent(@NonNull Context context, @NonNull CollectionDAO dao, @NonNull Content content) throws ContentNotRemovedException {
        Helper.assertNonUiThread();
        // Remove from DB
        // NB : start with DB to have a LiveData feedback, because file removal can take much time
        dao.deleteContent(content);

        if (content.isArchive()) { // Remove an archive
            DocumentFile archive = FileHelper.getFileFromSingleUriString(context, content.getStorageUri());
            if (null == archive)
                throw new FileNotRemovedException(content, "Failed to find archive " + content.getStorageUri());

            if (archive.delete()) {
                Timber.i("Archive removed : %s", content.getStorageUri());
            } else {
                throw new FileNotRemovedException(content, "Failed to delete archive " + content.getStorageUri());
            }

            // Remove the cover stored in the app's persistent folder
            File appFolder = context.getFilesDir();
            File[] images = appFolder.listFiles((dir, name) -> FileHelper.getFileNameWithoutExtension(name).equals(content.getId() + ""));
            if (images != null)
                for (File f : images) FileHelper.removeFile(f);
        } else if (/*isInLibrary(content.getStatus()) &&*/ !content.getStorageUri().isEmpty()) { // Remove a folder and its content
            // If the book has just starting being downloaded and there are no complete pictures on memory yet, it has no storage folder => nothing to delete
            DocumentFile folder = FileHelper.getFolderFromTreeUriString(context, content.getStorageUri());
            if (null == folder)
                throw new FileNotRemovedException(content, "Failed to find directory " + content.getStorageUri());

            if (folder.delete()) {
                Timber.i("Directory removed : %s", content.getStorageUri());
            } else {
                throw new FileNotRemovedException(content, "Failed to delete directory " + content.getStorageUri());
            }
        }
    }

    /**
     * Remove the given Content from the queue, disk and the DB
     *
     * @param context Context to be used
     * @param dao     DAO to be used
     * @param content Content to be removed
     * @throws ContentNotRemovedException in case an issue prevents the content from being actually removed
     */
    public static void removeQueuedContent(@NonNull Context context, @NonNull CollectionDAO dao, @NonNull Content content) throws ContentNotRemovedException {
        Helper.assertNonUiThread();

        // Check if the content is on top of the queue; if so, send a CANCEL event
        if (isInQueueTab(content.getStatus())) {
            List<QueueRecord> queue = dao.selectQueue();
            if (!queue.isEmpty() && queue.get(0).getContent().getTargetId() == content.getId())
                EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_CANCEL));

            // Remove from queue
            dao.deleteQueue(content);
        }

        // Remove content itself
        removeContent(context, dao, content);
    }

    /**
     * Add new content to the library
     *
     * @param dao     DAO to be used
     * @param content Content to add to the library
     * @return ID of the newly added Content
     */
    public static long addContent(
            @NonNull final Context context,
            @NonNull final CollectionDAO dao,
            @NonNull final Content content) {
        content.populateAuthor();
        long newContentId = dao.insertContent(content);
        content.setId(newContentId);

        // Perform group operations only if
        //   - the book is in the library (i.e. not queued)
        //   - the book is linked to no group from the given grouping
        if (Helper.getListFromPrimitiveArray(libraryStatus).contains(content.getStatus().getCode())) {
            List<Grouping> staticGroupings = Stream.of(Grouping.values()).filter(Grouping::canReorderBooks).toList();
            for (Grouping g : staticGroupings)
                if (content.getGroupItems(g).isEmpty()) {
                    if (g.equals(Grouping.ARTIST)) {
                        int nbGroups = (int) dao.countGroupsFor(g);
                        AttributeMap attrs = content.getAttributeMap();
                        List<Attribute> artists = new ArrayList<>();
                        List<Attribute> sublist = attrs.get(AttributeType.ARTIST);
                        if (sublist != null)
                            artists.addAll(sublist);
                        sublist = attrs.get(AttributeType.CIRCLE);
                        if (sublist != null)
                            artists.addAll(sublist);

                        if (artists.isEmpty()) { // Add to the "no artist" group if no artist has been found
                            Group group = GroupHelper.getOrCreateNoArtistGroup(context, dao);
                            GroupItem item = new GroupItem(content, group, -1);
                            dao.insertGroupItem(item);
                        } else {
                            for (Attribute a : artists) { // Add to the artist groups attached to the artists attributes
                                Group group = a.getGroup().getTarget();
                                if (null == group) {
                                    group = new Group(Grouping.ARTIST, a.getName(), ++nbGroups);
                                    group.setSubtype(a.getType().equals(AttributeType.ARTIST) ? Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS : Preferences.Constant.ARTIST_GROUP_VISIBILITY_GROUPS);
                                    if (!a.contents.isEmpty())
                                        group.picture.setTarget(a.contents.get(0).getCover());
                                }
                                GroupHelper.addContentToAttributeGroup(dao, group, a, content);
                            }
                        }
                    }
                }
        }

        // Extract the cover to the app's persistent folder if the book is an archive
        if (content.isArchive() && content.getImageFiles() != null) {
            DocumentFile archive = FileHelper.getFileFromSingleUriString(context, content.getStorageUri());
            if (archive != null) {
                try {
                    Disposable unarchiveDisposable = ArchiveHelper.extractArchiveEntriesRx(
                            context,
                            archive,
                            Stream.of(content.getCover().getFileUri().replace(content.getStorageUri() + File.separator, "")).toList(),
                            context.getFilesDir(),
                            Stream.of(newContentId + "").toList())
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.computation())
                            .subscribe(
                                    uri -> {
                                        Timber.i(">> Set cover for %s", content.getTitle());
                                        content.getCover().setFileUri(uri.toString());
                                        dao.replaceImageList(newContentId, content.getImageFiles());
                                    },
                                    Timber::e
                            );

                    // Not ideal, but better than attaching it to the calling service that may not have enough longevity
                    new Helper.LifecycleRxCleaner(unarchiveDisposable).publish();
                } catch (IOException e) {
                    Timber.w(e);
                }
            }
        }

        return newContentId;
    }

    /**
     * Remove the given pages from the disk and the DB
     *
     * @param images  Pages to be removed
     * @param dao     DAO to be used
     * @param context Context to be used
     */
    public static void removePages(@NonNull List<ImageFile> images, @NonNull CollectionDAO dao, @NonNull final Context context) {
        Helper.assertNonUiThread();
        // Remove from DB
        // NB : start with DB to have a LiveData feedback, because file removal can take much time
        dao.deleteImageFiles(images);

        // Remove the pages from disk
        for (ImageFile image : images)
            FileHelper.removeFile(context, Uri.parse(image.getFileUri()));

        // Lists all relevant content
        List<Long> contents = Stream.of(images).filter(i -> i.getContent() != null).map(i -> i.getContent().getTargetId()).distinct().toList();

        // Update content JSON if it exists (i.e. if book is not queued)
        for (Long contentId : contents) {
            Content content = dao.selectContent(contentId);
            if (content != null && !content.getJsonUri().isEmpty())
                updateContentJson(context, content);
        }
    }

    /**
     * Define a new cover among a Content's ImageFiles
     *
     * @param newCover ImageFile to be used as a cover for the Content it is related to
     * @param dao      DAO to be used
     * @param context  Context to be used
     */
    public static void setContentCover(@NonNull ImageFile newCover, @NonNull CollectionDAO dao, @NonNull final Context context) {
        Helper.assertNonUiThread();

        // Get all images from the DB
        Content content = dao.selectContent(newCover.getContent().getTargetId());
        if (null == content) return;
        List<ImageFile> images = content.getImageFiles();
        if (null == images) return;

        // Remove current cover from the set
        for (int i = 0; i < images.size(); i++)
            if (images.get(i).isCover()) {
                images.remove(i);
                break;
            }

        // Duplicate given picture and set it as a cover
        ImageFile cover = ImageFile.newCover(newCover.getUrl(), newCover.getStatus()).setFileUri(newCover.getFileUri()).setMimeType(newCover.getMimeType());
        images.add(0, cover);

        // Update cover URL to "ping" the content to be updated too (useful for library screen that only detects "direct" content updates)
        content.setCoverImageUrl(newCover.getUrl());

        // Update the whole list
        dao.insertContent(content);

        // Update content JSON if it exists (i.e. if book is not queued)
        if (!content.getJsonUri().isEmpty())
            updateContentJson(context, content);
    }

    /**
     * Create the download directory of the given content
     *
     * @param context Context
     * @param content Content for which the directory to create
     * @return Created directory
     */
    @Nullable
    public static DocumentFile getOrCreateContentDownloadDir(@NonNull Context context, @NonNull Content content) {
        DocumentFile siteDownloadDir = getOrCreateSiteDownloadDir(context, null, content.getSite());
        if (null == siteDownloadDir) return null;

        ImmutablePair<String, String> bookFolderName = formatBookFolderName(content);

        // First try finding the folder with new naming...
        DocumentFile bookFolder = FileHelper.findFolder(context, siteDownloadDir, bookFolderName.left);
        if (null == bookFolder) { // ...then with old (sanitized) naming...
            bookFolder = FileHelper.findFolder(context, siteDownloadDir, bookFolderName.right);
            if (null == bookFolder) { // ...if not, create a new folder with the new naming...
                DocumentFile result = siteDownloadDir.createDirectory(bookFolderName.left);
                if (null == result) { // ...if it fails, create a new folder with the old naming
                    return siteDownloadDir.createDirectory(bookFolderName.right);
                } else return result;
            }
        }
        return bookFolder;
    }

    /**
     * Format the download directory path of the given content according to current user preferences
     *
     * @param content Content to get the path from
     * @return Pair containing the canonical naming of the given content :
     * - Left side : Naming convention allowing non-ANSI characters
     * - Right side : Old naming convention with ANSI characters alone
     */
    public static ImmutablePair<String, String> formatBookFolderName(@NonNull final Content content) {
        String title = content.getTitle();
        title = (null == title) ? "" : title;
        String author = content.getAuthor().toLowerCase();

        return new ImmutablePair<>(
                formatBookFolderName(content, FileHelper.cleanFileName(title), FileHelper.cleanFileName(author)),
                formatBookFolderName(content, title.replaceAll(UNAUTHORIZED_CHARS, "_"), author.replaceAll(UNAUTHORIZED_CHARS, "_"))
        );
    }

    private static String formatBookFolderName(@NonNull final Content content, @NonNull final String title, @NonNull final String author) {
        String result = "";
        switch (Preferences.getFolderNameFormat()) {
            case Preferences.Constant.FOLDER_NAMING_CONTENT_TITLE_ID:
                result += title;
                break;
            case Preferences.Constant.FOLDER_NAMING_CONTENT_AUTH_TITLE_ID:
                result += author + " - " + title;
                break;
            case Preferences.Constant.FOLDER_NAMING_CONTENT_TITLE_AUTH_ID:
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

        // We always add the unique ID at the end of the folder name to avoid collisions between two books with the same title from the same source
        // (e.g. different scans, different languages)
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
        if (id.length() > 10) id = StringHelper.formatIntAsStr(Math.abs(id.hashCode()), 10);
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
    static DocumentFile getOrCreateSiteDownloadDir(@NonNull Context context, @Nullable FileExplorer explorer, @NonNull Site site) {
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
        if (null == explorer)
            siteFolder = FileHelper.findFolder(context, appFolder, siteFolderName);
        else
            siteFolder = explorer.findFolder(context, appFolder, siteFolderName);

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

    /**
     * Parse the given download parameters string into a map of strings
     *
     * @param downloadParamsStr String representation of the download parameters to parse
     * @return Map of strings describing the given download parameters
     */
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


    /**
     * Remove the leading zeroes and the file extension of the given string
     *
     * @param s String to be cleaned
     * @return Input string, without leading zeroes and extension
     */
    private static String removeLeadingZeroesAndExtension(@Nullable String s) {
        if (null == s) return "";

        int beginIndex = 0;
        if (s.startsWith("0")) beginIndex = -1;

        for (int i = 0; i < s.length(); i++) {
            if (-1 == beginIndex && s.charAt(i) != '0') beginIndex = i;
            if ('.' == s.charAt(i)) return s.substring(beginIndex, i);
        }

        return (-1 == beginIndex) ? "0" : s.substring(beginIndex);
    }

    /**
     * Remove the leading zeroes and the file extension of the given string using cached results
     *
     * @param s String to be cleaned
     * @return Input string, without leading zeroes and extension
     */
    private static String removeLeadingZeroesAndExtensionCached(String s) {
        if (fileNameMatchCache.containsKey(s)) return fileNameMatchCache.get(s);
        else {
            String result = removeLeadingZeroesAndExtension(s);
            fileNameMatchCache.put(s, result);
            return result;
        }
    }

    /**
     * Matches the given files to the given ImageFiles according to their name (without leading zeroes nor file extension)
     *
     * @param files  Files to be matched to the given ImageFiles
     * @param images ImageFiles to be matched to the given files
     * @return List of matched ImageFiles, with the Uri of the matching file
     */
    public static List<ImageFile> matchFilesToImageList(@NonNull final List<DocumentFile> files, @NonNull final List<ImageFile> images) {
        Map<String, ImmutablePair<String, Long>> fileNameProperties = new HashMap<>(files.size());
        List<ImageFile> result = new ArrayList<>();
        boolean coverFound = false;

        // Put file names into a Map to speed up the lookup
        for (DocumentFile file : files)
            fileNameProperties.put(removeLeadingZeroesAndExtensionCached(file.getName()), new ImmutablePair<>(file.getUri().toString(), file.length()));

        // Look up similar names between images and file names
        for (ImageFile img : images) {
            String imgName = removeLeadingZeroesAndExtensionCached(img.getName());
            ImmutablePair<String, Long> property = fileNameProperties.get(imgName);
            if (property != null) {
                if (imgName.equals(Consts.THUMB_FILE_NAME)) {
                    coverFound = true;
                    img.setIsCover(true);
                }
                result.add(img.setFileUri(property.left).setSize(property.right).setStatus(StatusContent.DOWNLOADED));
            } else
                Timber.i(">> img dropped %s", imgName);
        }

        // If no thumb found, set the 1st image as cover
        if (!coverFound && !result.isEmpty()) result.get(0).setIsCover(true);
        return result;
    }

    /**
     * Create the list of ImageFiles from the given folder
     *
     * @param context Context to be used
     * @param folder  Folder to read the images from
     * @return List of ImageFiles corresponding to all supported pictures inside the given folder, sorted numerically then alphabetically
     */
    public static List<ImageFile> createImageListFromFolder(@NonNull final Context context, @NonNull final DocumentFile folder) {
        List<DocumentFile> imageFiles = FileHelper.listFiles(context, folder, ImageHelper.getImageNamesFilter());
        if (!imageFiles.isEmpty())
            return createImageListFromFiles(imageFiles);
        else return Collections.emptyList();
    }

    /**
     * Create the list of ImageFiles from the given files
     *
     * @param files Files to find images into
     * @return List of ImageFiles corresponding to all supported pictures among the given files, sorted numerically then alphabetically
     */
    public static List<ImageFile> createImageListFromFiles(@NonNull final List<DocumentFile> files) {
        return createImageListFromFiles(files, StatusContent.DOWNLOADED, 0, "");
    }

    /**
     * Create the list of ImageFiles from the given files
     *
     * @param files         Files to find images into
     * @param targetStatus  Target status of the ImageFiles to create
     * @param startingOrder Starting order of the ImageFiles to create
     * @param namePrefix    Prefix to add in front of the name of the ImageFiles to create
     * @return List of ImageFiles corresponding to all supported pictures among the given files, sorted numerically then alphabetically
     */
    public static List<ImageFile> createImageListFromFiles(
            @NonNull final List<DocumentFile> files,
            @NonNull final StatusContent targetStatus,
            int startingOrder,
            @NonNull final String namePrefix) {
        Helper.assertNonUiThread();
        List<ImageFile> result = new ArrayList<>();
        int order = startingOrder;
        boolean coverFound = false;
        // Sort files by anything that resembles a number inside their names
        List<DocumentFile> fileList = Stream.of(files).withoutNulls().sorted(new InnerNameNumberFileComparator()).collect(toList());
        for (DocumentFile f : fileList) {
            String name = namePrefix + ((f.getName() != null) ? f.getName() : "");
            ImageFile img = new ImageFile();
            if (name.startsWith(Consts.THUMB_FILE_NAME)) {
                coverFound = true;
                img.setIsCover(true);
            } else order++;
            img.setName(FileHelper.getFileNameWithoutExtension(name)).setOrder(order).setUrl(f.getUri().toString()).setStatus(targetStatus).setFileUri(f.getUri().toString()).setSize(f.length());
            img.setMimeType(FileHelper.getMimeTypeFromFileName(name));
            result.add(img);
        }
        // If no thumb found, set the 1st image as cover
        if (!coverFound && !result.isEmpty()) result.get(0).setIsCover(true);
        return result;
    }

    /**
     * Create a list of ImageFiles from the given archive entries
     *
     * @param archiveFileUri Uri of the archive file the entries have been read from
     * @param files          Entries to create the ImageFile list with
     * @param targetStatus   Target status of the ImageFiles
     * @param startingOrder  Starting order of the first ImageFile to add; will be numbered incrementally from that number on
     * @param namePrefix     Prefix to add to image names
     * @return List of ImageFiles contructed from the given parameters
     */
    public static List<ImageFile> createImageListFromArchiveEntries(
            @NonNull final Uri archiveFileUri,
            @NonNull final List<ArchiveHelper.ArchiveEntry> files,
            @NonNull final StatusContent targetStatus,
            int startingOrder,
            @NonNull final String namePrefix) {
        Helper.assertNonUiThread();
        List<ImageFile> result = new ArrayList<>();
        int order = startingOrder;
        // Sort files by anything that resembles a number inside their names (default entry order from ZipInputStream is chaotic)
        List<ArchiveHelper.ArchiveEntry> fileList = Stream.of(files).withoutNulls().sorted(new InnerNameNumberArchiveComparator()).collect(toList());
        for (ArchiveHelper.ArchiveEntry f : fileList) {
            String name = namePrefix + f.path;
            String path = archiveFileUri.toString() + File.separator + f.path;
            ImageFile img = new ImageFile();
            if (name.startsWith(Consts.THUMB_FILE_NAME)) img.setIsCover(true);
            else order++;
            img.setName(FileHelper.getFileNameWithoutExtension(name)).setOrder(order).setUrl(path).setStatus(targetStatus).setFileUri(path).setSize(f.size);
            img.setMimeType(FileHelper.getMimeTypeFromFileName(name));
            result.add(img);
        }
        return result;
    }

    /**
     * Launch the web browser for the given site and URL
     * TODO make sure the URL and the site are compatible
     *
     * @param context   COntext to be used
     * @param s         Site to navigate to
     * @param targetUrl Url to navigate to
     */
    public static void launchBrowserFor(@NonNull final Context context, @NonNull final Site s, @NonNull final String targetUrl) {
        Intent intent = new Intent(context, Content.getWebActivityClass(s));

        BaseWebActivityBundle.Builder builder = new BaseWebActivityBundle.Builder();
        builder.setUrl(targetUrl);
        intent.putExtras(builder.getBundle());

        context.startActivity(intent);
    }

    /**
     * Get the blocked tags of the given Content
     * NB : Blocked tags are detected according to the current app Preferences
     *
     * @param content Content to extract blocked tags from
     * @return List of blocked tags from the given Content
     */
    public static List<String> getBlockedTags(@NonNull final Content content) {
        List<String> result = Collections.emptyList();
        if (!Preferences.getBlockedTags().isEmpty()) {
            List<String> tags = Stream.of(content.getAttributes()).filter(a -> a.getType().equals(AttributeType.TAG)).map(Attribute::getName).toList();
            for (String blocked : Preferences.getBlockedTags())
                for (String tag : tags)
                    if (blocked.equalsIgnoreCase(tag) || StringHelper.isPresentAsWord(blocked, tag)) {
                        if (result.isEmpty()) result = new ArrayList<>();
                        result.add(tag);
                        break;
                    }
        }
        return result;
    }

    // TODO doc
    public static Content reparseFromScratch(@NonNull final Content content) throws IOException {
        return reparseFromScratch(content, content.getGalleryUrl());
    }

    // TODO visual feedback to warn the user about redownload "from scratch" having failed (whenever the original content is returned)
    private static Content reparseFromScratch(@NonNull final Content content, @NonNull final String url) throws IOException {
        Helper.assertNonUiThread();

        String readerUrl = content.getReaderUrl();
        List<Pair<String, String>> requestHeadersList = new ArrayList<>();
        requestHeadersList.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, readerUrl));
        String cookieStr = HttpHelper.getCookies(url, requestHeadersList, content.getSite().useMobileAgent(), content.getSite().useHentoidAgent(), content.getSite().useWebviewAgent());
        if (!cookieStr.isEmpty())
            requestHeadersList.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));

        Response response = HttpHelper.getOnlineResource(url, requestHeadersList, content.getSite().useMobileAgent(), content.getSite().useHentoidAgent(), content.getSite().useWebviewAgent());

        // Scram if the response is a redirection or an error
        if (response.code() >= 300) return content;

        // Scram if the response is something else than html
        Pair<String, String> contentType = HttpHelper.cleanContentType(response.header(HEADER_CONTENT_TYPE, ""));
        if (!contentType.first.isEmpty() && !contentType.first.equals("text/html"))
            return content;

        // Scram if the response is empty
        ResponseBody body = response.body();
        if (null == body) return content;

        InputStream parserStream = body.byteStream();

        Class<? extends ContentParser> c = ContentParserFactory.getInstance().getContentParserClass(content.getSite());
        final Jspoon jspoon = Jspoon.create();
        HtmlAdapter<? extends ContentParser> htmlAdapter = jspoon.adapter(c); // Unchecked but alright

        ContentParser contentParser = htmlAdapter.fromInputStream(parserStream, new URL(url));
        Content newContent = contentParser.update(content, url);

        if (newContent.getStatus() != null && newContent.getStatus().equals(StatusContent.IGNORED)) {
            String canonicalUrl = contentParser.getCanonicalUrl();
            if (!canonicalUrl.isEmpty() && !canonicalUrl.equalsIgnoreCase(url))
                return reparseFromScratch(content, canonicalUrl);
            else return content;
        }

        // Save cookies for future calls during download
        Map<String, String> params = new HashMap<>();
        if (!cookieStr.isEmpty()) params.put(HttpHelper.HEADER_COOKIE_KEY, cookieStr);

        newContent.setDownloadParams(JsonHelper.serializeToJson(params, JsonHelper.MAP_STRINGS));
        return newContent;
    }

    // TODO doc
    public static void purgeFiles(@NonNull final Context context, @NonNull final Content content) {
        DocumentFile bookFolder = FileHelper.getFolderFromTreeUriString(context, content.getStorageUri());
        if (bookFolder != null) {
            List<DocumentFile> files = FileHelper.listFiles(context, bookFolder, null); // Remove everything (incl. JSON and thumb)
            if (!files.isEmpty())
                for (DocumentFile file : files) file.delete();
        }
    }

    // TODO doc
    public static String formatTags(@NonNull final Content content) {
        List<Attribute> tagsAttributes = content.getAttributeMap().get(AttributeType.TAG);
        if (tagsAttributes == null) return "";

        List<String> allTags = new ArrayList<>();
        for (Attribute attribute : tagsAttributes) {
            allTags.add(attribute.getName());
        }
        if (Build.VERSION.SDK_INT >= 24) {
            allTags.sort(null);
        }
        return android.text.TextUtils.join(", ", allTags);
    }

    @Nullable
    public static Content findDuplicate(@NonNull final CollectionDAO dao, @NonNull final Content content) {
        // First find good rough candidates by searching for the longest word in the title
        String[] words = StringHelper.cleanMultipleSpaces(StringHelper.cleanup(content.getTitle())).split(" ");
        Optional<String> longestWord = Stream.of(words).sorted((o1, o2) -> Integer.compare(o1.length(), o2.length())).findLast();
        if (longestWord.isEmpty()) return null;

        int[] contentStatuses = ArrayUtils.addAll(libraryStatus, queueTabStatus);
        List<Content> roughCandidates = dao.searchTitlesWith(longestWord.get(), contentStatuses);
        if (roughCandidates.isEmpty()) return null;

        // Refine by running the actual duplicate detection algorithm against the rough candidates
        // TODO

        return roughCandidates.get(0);
    }

    /**
     * Comparator to be used to sort files according to their names
     */
    private static class InnerNameNumberFileComparator implements Comparator<DocumentFile> {
        @Override
        public int compare(@NonNull DocumentFile o1, @NonNull DocumentFile o2) {
            return CaseInsensitiveSimpleNaturalComparator.getInstance().compare(StringHelper.protect(o1.getName()), StringHelper.protect(o2.getName()));
        }
    }

    /**
     * Comparator to be used to sort archive entries according to their names
     */
    private static class InnerNameNumberArchiveComparator implements Comparator<ArchiveHelper.ArchiveEntry> {
        @Override
        public int compare(@NonNull ArchiveHelper.ArchiveEntry o1, @NonNull ArchiveHelper.ArchiveEntry o2) {
            return CaseInsensitiveSimpleNaturalComparator.getInstance().compare(o1.path, o2.path);
        }
    }
}
