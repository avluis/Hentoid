package me.devsaki.hentoid.util;

import static me.devsaki.hentoid.util.network.HttpHelper.HEADER_CONTENT_TYPE;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.MimeTypeMap;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
//import com.google.firebase.crashlytics.FirebaseCrashlytics;

import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.threeten.bp.Instant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URL;
import java.text.Collator;
import java.util.Arrays;
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
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.DuplicateEntry;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.util.exception.ContentNotProcessedException;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.FileNotProcessedException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.util.string_similarity.Cosine;
import me.devsaki.hentoid.util.string_similarity.StringSimilarity;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pl.droidsonroids.jspoon.HtmlAdapter;
import pl.droidsonroids.jspoon.Jspoon;
import timber.log.Timber;

/**
 * Utility class for Content-related operations
 */
public final class ContentHelper {

    @IntDef({QueuePosition.TOP, QueuePosition.BOTTOM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface QueuePosition {
        int TOP = Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_TOP;
        int BOTTOM = Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM;
    }

    public static final String KEY_DL_PARAMS_NB_CHAPTERS = "nbChapters";
    public static final String KEY_DL_PARAMS_UGOIRA_FRAMES = "ugo_frames";

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

    public static int[] getQueueTabStatuses() {
        return queueTabStatus;
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
        BaseWebActivityBundle bundle = new BaseWebActivityBundle();
        bundle.setUrl(content.getGalleryUrl());
        intent.putExtras(bundle.toBundle());
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
     * @param context Context to use
     * @param content Content whose JSON file to create
     * @return Created JSON file, or null if it couldn't be created
     */
    @Nullable
    public static DocumentFile createContentJson(@NonNull Context context, @NonNull Content content) {
        Helper.assertNonUiThread();
        if (content.isArchive())
            return null; // Keep that as is, we can't find the parent folder anyway

        DocumentFile folder = FileHelper.getFolderFromTreeUriString(context, content.getStorageUri());
        if (null == folder) return null;
        try {
            DocumentFile newJson = JsonHelper.jsonToFile(context, JsonContent.fromEntity(content), JsonContent.class, folder);
            content.setJsonUri(newJson.getUri().toString());
            return newJson;
        } catch (IOException e) {
            Timber.e(e, "Error while writing to %s", content.getStorageUri());
        }
        return null;
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
            //FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            //crashlytics.recordException(e);
            return false;
        }
        return true;
    }

     /**
      *  @param pageNumber   Page number to view
      */
    public static boolean openHentoidViewer(
            @NonNull Context context,
            @NonNull Content content,
            int pageNumber,
            Bundle searchParams,
            boolean forceShowGallery) {
        ImageViewerActivityBundle.Builder builder = new ImageViewerActivityBundle.Builder();
        builder.setContentId(content.getId());
        if (searchParams != null) builder.setSearchParams(searchParams);
        if (pageNumber > -1) builder.setPageNumber(pageNumber);
        builder.setForceShowGallery(forceShowGallery);

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

//        return FileHelper.listDocumentFiles(context, folder, ImageHelper.getImageNamesFilter());
    }

    /**
     * Remove the given Content from the disk and the DB
     *
     * @param context Context to be used
     * @param dao     DAO to be used
     * @param content Content to be removed
     * @throws ContentNotProcessedException in case an issue prevents the content from being actually removed
     */
    public static void removeContent(@NonNull Context context, @NonNull CollectionDAO dao, @NonNull Content content) throws ContentNotProcessedException {
        Helper.assertNonUiThread();
        // Remove from DB
        // NB : start with DB to have a LiveData feedback, because file removal can take much time
        dao.deleteContent(content);

        if (content.isArchive()) { // Remove an archive
            DocumentFile archive = FileHelper.getFileFromSingleUriString(context, content.getStorageUri());
            if (null == archive)
                throw new FileNotProcessedException(content, "Failed to find archive " + content.getStorageUri());

            if (archive.delete()) {
                Timber.i("Archive removed : %s", content.getStorageUri());
            } else {
                throw new FileNotProcessedException(content, "Failed to delete archive " + content.getStorageUri());
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
                throw new FileNotProcessedException(content, "Failed to find directory " + content.getStorageUri());

            if (folder.delete()) {
                Timber.i("Directory removed : %s", content.getStorageUri());
            } else {
                throw new FileNotProcessedException(content, "Failed to delete directory " + content.getStorageUri());
            }
        }
    }

    /**
     * Remove the given Content
     * - from the queue
     * - from disk and the DB (optional)
     *
     * @param context       Context to be used
     * @param dao           DAO to be used
     * @param content       Content to be removed
     * @param deleteContent If true, the content itself is deleted from disk and DB
     * @throws ContentNotProcessedException in case an issue prevents the content from being actually removed
     */
    public static void removeQueuedContent(@NonNull Context context, @NonNull CollectionDAO dao, @NonNull Content content, boolean deleteContent) throws ContentNotProcessedException {
        Helper.assertNonUiThread();

        // Check if the content is on top of the queue; if so, send a CANCEL event
        if (isInQueueTab(content.getStatus())) {
            List<QueueRecord> queue = dao.selectQueue();
            if (!queue.isEmpty() && queue.get(0).getContent().getTargetId() == content.getId())
                EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.Type.EV_CANCEL));

            // Remove from queue
            dao.deleteQueue(content);
        }

        // Remove content itself
        if (deleteContent) removeContent(context, dao, content);
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
                                        group.coverContent.setTarget(a.contents.get(0));
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
                            Stream.of(newContentId + "").toList(),
                            null)
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
        String author = formatBookAuthor(content).toLowerCase();

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
            default:
                // Nothing to do
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
        // For certain sources (8muses), unique IDs are strings that may be very long
        // => shorten them by using their hashCode
        if (id.length() > 10) id = StringHelper.formatIntAsStr(Math.abs(id.hashCode()), 10);
        return "[" + id + "]";
    }

    public static String formatBookAuthor(@NonNull final Content content) {
        String result = "";
        AttributeMap attrMap = content.getAttributeMap();
        // Try and get first Artist
        List<Attribute> artistAttributes = attrMap.get(AttributeType.ARTIST);
        if (artistAttributes != null && !artistAttributes.isEmpty())
            result = artistAttributes.get(0).getName();

        // If no Artist found, try and get first Circle
        if (null == result || result.isEmpty()) {
            List<Attribute> circleAttributes = attrMap.get(AttributeType.CIRCLE);
            if (circleAttributes != null && !circleAttributes.isEmpty())
                result = circleAttributes.get(0).getName();
        }

        return StringHelper.protect(result);
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
            if ('.' == s.charAt(i)) return (-1 == beginIndex) ? "0" : s.substring(beginIndex, i);
            if (-1 == beginIndex && s.charAt(i) != '0') beginIndex = i;
        }

        return (-1 == beginIndex) ? "0" : s.substring(beginIndex);
    }

    /**
     * Remove the leading zeroes and the file extension of the given string using cached results
     *
     * @param s String to be cleaned
     * @return Input string, without leading zeroes and extension
     */
    private static String removeLeadingZeroesAndExtensionCached(@NonNull final String s) {
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
            if (file.getName() != null)
                fileNameProperties.put(removeLeadingZeroesAndExtensionCached(file.getName()), new ImmutablePair<>(file.getUri().toString(), file.length()));

        // Look up similar names between images and file names
        int order;
        int previousOrder = -1;
        for (int i = 0; i < images.size(); i++) {
            ImageFile img = images.get(i);
            String imgName = removeLeadingZeroesAndExtensionCached(img.getName());

            ImmutablePair<String, Long> property;
            boolean isOnline = img.getStatus().equals(StatusContent.ONLINE);
            if (isOnline) {
                property = new ImmutablePair<>("", 0L);
            } else {
                // Detect gaps inside image numbering
                order = img.getOrder();
                // Look for files named with the forgotten number
                if (previousOrder > -1 && previousOrder < order - 1) {
                    Timber.i("Numbering gap detected : %d to %d", previousOrder, order);
                    for (int j = previousOrder + 1; j < order; j++) {
                        ImmutablePair<String, Long> localProperty = fileNameProperties.get(j + "");
                        if (localProperty != null) {
                            Timber.i("Numbering gap filled with a file : %d", j);
                            ImageFile newImage = ImageFile.fromImageUrl(j, images.get(i - 1).getUrl(), StatusContent.DOWNLOADED, images.size());
                            newImage.setFileUri(localProperty.left).setSize(localProperty.right);
                            result.add(result.size() - 1, newImage);
                        }
                    }
                }
                previousOrder = order;

                property = fileNameProperties.get(imgName);
            }
            if (property != null) {
                if (imgName.equals(Consts.THUMB_FILE_NAME)) {
                    coverFound = true;
                    img.setIsCover(true);
                }
                result.add(img.setFileUri(property.left).setSize(property.right).setStatus(isOnline ? StatusContent.ONLINE : StatusContent.DOWNLOADED));
            } else
                Timber.i(">> image not found among files : %s", imgName);
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
        List<DocumentFile> fileList = Stream.of(files).withoutNulls().sorted(new InnerNameNumberFileComparator()).toList();
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
        List<ArchiveHelper.ArchiveEntry> fileList = Stream.of(files).withoutNulls().sorted(new InnerNameNumberArchiveComparator()).toList();
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
     *
     * @param context   COntext to be used
     * @param targetUrl Url to navigate to
     */
    public static void launchBrowserFor(@NonNull final Context context, @NonNull final String targetUrl) {
        Site targetSite = Site.searchByUrl(targetUrl);
        if (null == targetSite || targetSite.equals(Site.NONE)) return;

        Intent intent = new Intent(context, Content.getWebActivityClass(targetSite));

        BaseWebActivityBundle bundle = new BaseWebActivityBundle();
        bundle.setUrl(targetUrl);
        intent.putExtras(bundle.toBundle());

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
            List<String> tags = Stream.of(content.getAttributes())
                    .filter(a -> a.getType().equals(AttributeType.TAG) || a.getType().equals(AttributeType.LANGUAGE))
                    .map(Attribute::getName)
                    .toList();
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

    /**
     * Update the given content's properties by parsing its webpage
     *
     * @param content Content to parse again from its online source
     * @return Content updated from its online source, or Optional.empty if something went wrong
     */
    public static ImmutablePair<Content, Optional<Content>> reparseFromScratch(@NonNull final Content content) {
        try {
            return new ImmutablePair<>(content, reparseFromScratch(content, content.getGalleryUrl()));
        } catch (IOException e) {
            Timber.w(e);
            return new ImmutablePair<>(content, Optional.empty());
        }
    }

    /**
     * Parse the given webpage to update the given Content's properties
     *
     * @param content Content which properties to update
     * @param url     Webpage to parse to update the given Content's properties
     * @return Content with updated properties, or Optional.empty if something went wrong
     * @throws IOException If something horrible happens during parsing
     */
    private static Optional<Content> reparseFromScratch(@NonNull final Content content, @NonNull final String url) throws IOException {
        Helper.assertNonUiThread();

        String readerUrl = content.getReaderUrl();
        List<Pair<String, String>> requestHeadersList = new ArrayList<>();
        requestHeadersList.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, readerUrl));
        String cookieStr = HttpHelper.getCookies(url, requestHeadersList, content.getSite().useMobileAgent(), content.getSite().useHentoidAgent(), content.getSite().useWebviewAgent());
        if (!cookieStr.isEmpty())
            requestHeadersList.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));

        Response response = HttpHelper.getOnlineResourceFast(url, requestHeadersList, content.getSite().useMobileAgent(), content.getSite().useHentoidAgent(), content.getSite().useWebviewAgent());

        // Scram if the response is a redirection or an error
        if (response.code() >= 300) return Optional.empty();

        // Scram if the response is something else than html
        Pair<String, String> contentType = HttpHelper.cleanContentType(StringHelper.protect(response.header(HEADER_CONTENT_TYPE, "")));
        if (!contentType.first.isEmpty() && !contentType.first.equals("text/html"))
            return Optional.empty();

        // Scram if the response is empty
        ResponseBody body = response.body();
        if (null == body) return Optional.empty();

        Class<? extends ContentParser> c = ContentParserFactory.getInstance().getContentParserClass(content.getSite());
        final Jspoon jspoon = Jspoon.create();
        HtmlAdapter<? extends ContentParser> htmlAdapter = jspoon.adapter(c); // Unchecked but alright

        ContentParser contentParser = htmlAdapter.fromInputStream(body.byteStream(), new URL(url));
        Content newContent = contentParser.update(content, url, true);

        if (newContent.getStatus() != null && newContent.getStatus().equals(StatusContent.IGNORED)) {
            String canonicalUrl = contentParser.getCanonicalUrl();
            if (!canonicalUrl.isEmpty() && !canonicalUrl.equalsIgnoreCase(url))
                return reparseFromScratch(content, canonicalUrl);
            else return Optional.empty();
        }

        // Clear existing chapters to avoid issues with extra chapter detection
        newContent.clearChapters();

        // Save cookies for future calls during download
        Map<String, String> params = new HashMap<>();
        if (!cookieStr.isEmpty()) params.put(HttpHelper.HEADER_COOKIE_KEY, cookieStr);

        newContent.setDownloadParams(JsonHelper.serializeToJson(params, JsonHelper.MAP_STRINGS));
        return Optional.of(newContent);
    }

    /**
     * Query source to fetch all image file names and URLs of a given book
     *
     * @param content Book whose pages to retrieve
     * @return List of pages with original URLs and file name
     */
    public static List<ImageFile> fetchImageURLs(@NonNull Content content, @NonNull StatusContent targetImageStatus) throws Exception {
        List<ImageFile> imgs;

        // If content doesn't have any download parameters, get them from the cookie manager
        String contentDownloadParamsStr = content.getDownloadParams();
        if (null == contentDownloadParamsStr || contentDownloadParamsStr.isEmpty()) {
            String cookieStr = HttpHelper.getCookies(content.getGalleryUrl());
            if (!cookieStr.isEmpty()) {
                Map<String, String> downloadParams = new HashMap<>();
                downloadParams.put(HttpHelper.HEADER_COOKIE_KEY, cookieStr);
                content.setDownloadParams(JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS));
            }
        }

        // Use ImageListParser to query the source
        ImageListParser parser = ContentParserFactory.getInstance().getImageListParser(content);
        imgs = parser.parseImageList(content);

        // If no images found, or just the cover, image detection has failed
        if (imgs.isEmpty() || (1 == imgs.size() && imgs.get(0).isCover()))
            throw new EmptyResultException();

        // Add the content's download params to the images only if they have missing information
        contentDownloadParamsStr = content.getDownloadParams();
        if (contentDownloadParamsStr != null && contentDownloadParamsStr.length() > 2) {
            Map<String, String> contentDownloadParams = ContentHelper.parseDownloadParams(contentDownloadParamsStr);
            for (ImageFile i : imgs) {
                if (i.getDownloadParams() != null && i.getDownloadParams().length() > 2) {
                    Map<String, String> imageDownloadParams = ContentHelper.parseDownloadParams(i.getDownloadParams());
                    // Content's params
                    for (Map.Entry<String, String> entry : contentDownloadParams.entrySet())
                        if (!imageDownloadParams.containsKey(entry.getKey()))
                            imageDownloadParams.put(entry.getKey(), entry.getValue());
                    // Referer, just in case
                    if (!imageDownloadParams.containsKey(HttpHelper.HEADER_REFERER_KEY))
                        imageDownloadParams.put(HttpHelper.HEADER_REFERER_KEY, content.getSite().getUrl());
                    i.setDownloadParams(JsonHelper.serializeToJson(imageDownloadParams, JsonHelper.MAP_STRINGS));
                } else {
                    i.setDownloadParams(contentDownloadParamsStr);
                }
            }
        }

        // Cleanup generated objects
        for (ImageFile img : imgs) {
            img.setId(0);
            img.setStatus(targetImageStatus);
            img.setContentId(content.getId());
        }

        return imgs;
    }

    /**
     * Remove all files (including JSON and cover thumb) from the given Content's folder
     * The folder itself is left empty
     * <p>
     * Caution : exec time is long
     *
     * @param context Context to use
     * @param content Content to remove files from
     */
    public static void purgeFiles(
            @NonNull final Context context,
            @NonNull final Content content,
            boolean removeJson,
            boolean keepCover) {
        DocumentFile bookFolder = FileHelper.getFolderFromTreeUriString(context, content.getStorageUri());
        if (bookFolder != null) {
            List<DocumentFile> files = FileHelper.listFiles(context, bookFolder, displayName -> !keepCover || !displayName.startsWith(Consts.THUMB_FILE_NAME));
            if (!files.isEmpty())
                for (DocumentFile file : files)
                    if (removeJson || !HttpHelper.getExtensionFromUri(file.getUri().toString()).toLowerCase().endsWith("json"))
                        file.delete();
        }
    }

    /**
     * Format the given Content's tags for display
     *
     * @param content Content to get the formatted tags for
     * @return Given Content's tags formatted for display
     */
    public static String formatTagsForDisplay(@NonNull final Content content) {
        List<Attribute> tagsAttributes = content.getAttributeMap().get(AttributeType.TAG);
        if (tagsAttributes == null) return "";

        List<String> allTags = Stream.of(tagsAttributes).map(Attribute::getName).sorted().limit(30).toList();

        return android.text.TextUtils.join(", ", allTags);
    }

    /**
     * Get the resource ID for the given Content's language flag
     *
     * @param context Context to use
     * @param content Content to get the flag resource ID for
     * @return Resource ID (DrawableRes) of the given Content's language flag; 0 if no matching flag found
     */
    public static @DrawableRes
    int getFlagResourceId(@NonNull final Context context, @NonNull final Content content) {
        List<Attribute> langAttributes = content.getAttributeMap().get(AttributeType.LANGUAGE);
        if (langAttributes != null && !langAttributes.isEmpty())
            for (Attribute lang : langAttributes) {
                @DrawableRes int resId = LanguageHelper.getFlagFromLanguage(context, lang.getName());
                if (resId != 0) return resId;
            }
        return 0;
    }

    /**
     * Format the given Content's artists for display
     *
     * @param context Context to use
     * @param content Content to get the formatted artists for
     * @return Given Content's artists formatted for display
     */
    public static String formatArtistForDisplay(@NonNull final Context context, @NonNull final Content content) {
        List<Attribute> attributes = new ArrayList<>();

        List<Attribute> artistAttributes = content.getAttributeMap().get(AttributeType.ARTIST);
        if (artistAttributes != null)
            attributes.addAll(artistAttributes);
        List<Attribute> circleAttributes = content.getAttributeMap().get(AttributeType.CIRCLE);
        if (circleAttributes != null)
            attributes.addAll(circleAttributes);

        if (attributes.isEmpty()) {
            return context.getString(R.string.work_artist, context.getResources().getString(R.string.work_untitled));
        } else {
            List<String> allArtists = new ArrayList<>();
            for (Attribute attribute : attributes) {
                allArtists.add(attribute.getName());
            }
            String artists = android.text.TextUtils.join(", ", allArtists);
            return context.getString(R.string.work_artist, artists);
        }
    }

    /**
     * Find the best match for the given Content inside the library and queue
     *
     * @param context Context to use
     * @param content Content to find the duplicate for
     * @param pHash   Cover perceptual hash to use as an override for the given Content's cover hash; Long.MIN_VALUE not to override
     * @param dao     DAO to use
     * @return Pair containing
     * - left side : Best match for the given Content inside the library and queue
     * - Right side : Similarity score (between 0 and 1; 1=100%)
     */
    @Nullable
    public static ImmutablePair<Content, Float> findDuplicate(
            @NonNull final Context context,
            @NonNull final Content content,
            long pHash,
            @NonNull final CollectionDAO dao) {
        // First find good rough candidates by searching for the longest word in the title
        String[] words = StringHelper.cleanMultipleSpaces(StringHelper.cleanup(content.getTitle())).split(" ");
        Optional<String> longestWord = Stream.of(words).sorted((o1, o2) -> Integer.compare(o1.length(), o2.length())).findLast();
        if (longestWord.isEmpty()) return null;

        int[] contentStatuses = ArrayUtils.addAll(libraryStatus, queueTabStatus);
        List<Content> roughCandidates = dao.searchTitlesWith(longestWord.get(), contentStatuses);
        if (roughCandidates.isEmpty()) return null;

        // Compute cover hashes for selected candidates
        for (Content c : roughCandidates)
            if (0 == c.getCover().getImageHash()) computeAndSaveCoverHash(context, c, dao);

        // Refine by running the actual duplicate detection algorithm against the rough candidates
        List<DuplicateEntry> entries = new ArrayList<>();
        StringSimilarity cosine = new Cosine();
        // TODO make useLanguage a setting ?
        DuplicateHelper.DuplicateCandidate reference = new DuplicateHelper.DuplicateCandidate(content, true, true, false, pHash);
        List<DuplicateHelper.DuplicateCandidate> candidates = Stream.of(roughCandidates).map(c -> new DuplicateHelper.DuplicateCandidate(c, true, true, false, Long.MIN_VALUE)).toList();
        for (DuplicateHelper.DuplicateCandidate candidate : candidates) {
            DuplicateEntry entry = DuplicateHelper.Companion.processContent(reference, candidate, true, true, true, false, true, 2, cosine);
            if (entry != null) entries.add(entry);
        }
        // Sort by similarity and size (unfortunately, Comparator.comparing is API24...)
        Optional<DuplicateEntry> bestMatch = Stream.of(entries).sorted(DuplicateEntry::compareTo).findFirst();
        if (bestMatch.isPresent()) {
            Content resultContent = dao.selectContent(bestMatch.get().getDuplicateId());
            float resultScore = bestMatch.get().calcTotalScore();
            return new ImmutablePair<>(resultContent, resultScore);
        }

        return null;
    }

    /**
     * Compute perceptual hash for the cover picture
     *
     * @param context Context to use
     * @param content Content to process
     * @param dao     Dao used to save cover hash
     */
    public static void computeAndSaveCoverHash(
            @NonNull final Context context,
            @NonNull final Content content,
            @NonNull final CollectionDAO dao) {
        Bitmap coverBitmap = DuplicateHelper.Companion.getCoverBitmapFromContent(context, content);
        long pHash = DuplicateHelper.Companion.calcPhash(DuplicateHelper.Companion.getHashEngine(), coverBitmap);
        if (coverBitmap != null) coverBitmap.recycle();
        content.getCover().setImageHash(pHash);
        dao.insertImageFile(content.getCover());
    }

    /**
     * Test if online pages for the given Content are downloadable
     * NB : Implementation does not test all pages but one page picked randomly
     *
     * @param content Content whose pages to test
     * @return True if pages are downloadable; false if they aren't
     */
    public static boolean isDownloadable(@NonNull final Content content) {
        List<ImageFile> images = content.getImageFiles();
        if (null == images) return false;

        // Pick a random picture
        ImageFile img = images.get(Helper.getRandomInt(images.size()));

        // Peek it to see if downloads work
        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, content.getReaderUrl())); // Useful for Hitomi and Toonily

        try {
            if (img.needsPageParsing()) {
                // Get cookies from the app jar
                String cookieStr = HttpHelper.getCookies(img.getPageUrl());
                // If nothing found, peek from the site
                if (cookieStr.isEmpty())
                    cookieStr = HttpHelper.peekCookies(img.getPageUrl());
                if (!cookieStr.isEmpty())
                    headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));
                return testDownloadPictureFromPage(content.getSite(), img, headers);
            } else {
                // Get cookies from the app jar
                String cookieStr = HttpHelper.getCookies(img.getUrl());
                // If nothing found, peek from the site
                if (cookieStr.isEmpty())
                    cookieStr = HttpHelper.peekCookies(content.getGalleryUrl());
                if (!cookieStr.isEmpty())
                    headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));
                return testDownloadPicture(content.getSite(), img, headers);
            }
        } catch (IOException | LimitReachedException | EmptyResultException e) {
            Timber.w(e);
        }
        return false;
    }

    /**
     * Test if the given picture is downloadable using its page URL
     *
     * @param site           Corresponding Site
     * @param img            Picture to test
     * @param requestHeaders Request headers to use
     * @return True if the given picture is downloadable; false if not
     * @throws IOException           If something happens during the download attempt
     * @throws LimitReachedException If the site's download limit has been reached
     * @throws EmptyResultException  If no picture has been detected
     */
    public static boolean testDownloadPictureFromPage(@NonNull Site site,
                                                      @NonNull ImageFile img,
                                                      List<Pair<String, String>> requestHeaders) throws IOException, LimitReachedException, EmptyResultException {
        String pageUrl = HttpHelper.fixUrl(img.getPageUrl(), site.getUrl());
        ImageListParser parser = ContentParserFactory.getInstance().getImageListParser(site);
        ImmutablePair<String, Optional<String>> pages = parser.parseImagePage(pageUrl, requestHeaders);
        img.setUrl(pages.left);
        // Download the picture
        try {
            return testDownloadPicture(site, img, requestHeaders);
        } catch (IOException e) {
            if (pages.right.isPresent()) Timber.d("First download failed; trying backup URL");
            else throw e;
        }
        // Trying with backup URL
        img.setUrl(pages.right.get());
        return testDownloadPicture(site, img, requestHeaders);
    }

    /**
     * Test if the given picture is downloadable using its own URL
     *
     * @param site           Corresponding Site
     * @param img            Picture to test
     * @param requestHeaders Request headers to use
     * @return True if the given picture is downloadable; false if not
     * @throws IOException If something happens during the download attempt
     */
    public static boolean testDownloadPicture(
            @NonNull Site site,
            @NonNull ImageFile img,
            List<Pair<String, String>> requestHeaders) throws IOException {

        Response response = HttpHelper.getOnlineResourceFast(img.getUrl(), requestHeaders, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
        if (response.code() >= 300) throw new IOException("Network error " + response.code());

        ResponseBody body = response.body();
        if (null == body)
            throw new IOException("Could not read response : empty body for " + img.getUrl());

        byte[] buffer = new byte[50];
        // Read mime-type on the fly
        try (InputStream in = body.byteStream()) {
            if (in.read(buffer) > -1) {
                String mimeType = ImageHelper.getMimeTypeFromPictureBinary(buffer);
                Timber.d("Testing online picture accessibility : found %s at %s", mimeType, img.getUrl());
                return (!mimeType.isEmpty() && !mimeType.equals(ImageHelper.MIME_IMAGE_GENERIC));
            }
        }
        return false;
    }

    /**
     * Merge the given list of Content into one single new Content with the given title
     * NB : The Content's of the given list are _not_ removed
     *
     * @param context     Context to use
     * @param contentList List of Content to merge together
     * @param newTitle    Title of the new merged Content
     * @param dao         DAO to use
     * @throws ContentNotProcessedException If something terrible happens
     */
    public static void mergeContents(
            @NonNull Context context,
            @NonNull List<Content> contentList,
            @NonNull String newTitle,
            @NonNull final CollectionDAO dao) throws ContentNotProcessedException {
        Helper.assertNonUiThread();

        // New book inherits properties of the first content of the list
        // which takes "precedence" as the 1st chapter
        Content firstContent = contentList.get(0);

        // Initiate a new Content
        Content mergedContent = new Content();
        mergedContent.setSite(firstContent.getSite());
        mergedContent.setUrl(firstContent.getUrl());
        mergedContent.setUniqueSiteId(firstContent.getUniqueSiteId() + "_"); // Not to create a copy of firstContent
        mergedContent.setDownloadMode(firstContent.getDownloadMode());
        mergedContent.setTitle(newTitle);
        mergedContent.setCoverImageUrl(firstContent.getCoverImageUrl());
        mergedContent.setUploadDate(firstContent.getUploadDate());
        mergedContent.setDownloadDate(Instant.now().toEpochMilli());
        mergedContent.setStatus(firstContent.getStatus());
        mergedContent.setFavourite(firstContent.isFavourite());
        mergedContent.setBookPreferences(firstContent.getBookPreferences());
        mergedContent.setManuallyMerged(true);

        // Merge attributes
        List<Attribute> mergedAttributes = Stream.of(contentList).flatMap(c -> Stream.of(c.getAttributes())).toList();
        mergedContent.addAttributes(mergedAttributes);

        // Create destination folder for new content
        DocumentFile targetFolder;
        // External library root for external content
        if (mergedContent.getStatus().equals(StatusContent.EXTERNAL)) {
            DocumentFile externalRootFolder = FileHelper.getFolderFromTreeUriString(context, Preferences.getExternalLibraryUri());
            if (null == externalRootFolder || !externalRootFolder.exists())
                throw new ContentNotProcessedException(mergedContent, "Could not create target directory : external root unreachable");

            ImmutablePair<String, String> bookFolderName = formatBookFolderName(mergedContent);
            // First try finding the folder with new naming...
            targetFolder = FileHelper.findFolder(context, externalRootFolder, bookFolderName.left);
            if (null == targetFolder) { // ...then with old (sanitized) naming...
                targetFolder = FileHelper.findFolder(context, externalRootFolder, bookFolderName.right);
                if (null == targetFolder) { // ...if not, create a new folder with the new naming...
                    targetFolder = externalRootFolder.createDirectory(bookFolderName.left);
                    if (null == targetFolder) { // ...if it fails, create a new folder with the old naming
                        targetFolder = externalRootFolder.createDirectory(bookFolderName.right);
                    }
                }
            }
        } else { // Hentoid download folder for non-external content
            targetFolder = ContentHelper.getOrCreateContentDownloadDir(context, mergedContent);
        }
        if (null == targetFolder || !targetFolder.exists())
            throw new ContentNotProcessedException(mergedContent, "Could not create target directory");

        mergedContent.setStorageUri(targetFolder.getUri().toString());

        // Renumber all picture files and dispatch chapters
        long nbImages = Stream.of(contentList).map(Content::getImageFiles).withoutNulls().flatMap(Stream::of).filter(ImageFile::isReadable).count();
        int nbMaxDigits = (int) (Math.floor(Math.log10(nbImages)) + 1);

        List<ImageFile> mergedImages = new ArrayList<>();
        List<Chapter> mergedChapters = new ArrayList<>();

        ImageFile firstCover = firstContent.getCover();
        ImageFile coverPic = ImageFile.newCover(firstCover.getUrl(), firstCover.getStatus());
        boolean isError = false;
        try {
            // Set cover
            if (isInLibrary(coverPic.getStatus())) {
                String extension = HttpHelper.getExtensionFromUri(firstCover.getFileUri());
                Uri newUri = FileHelper.copyFile(
                        context,
                        Uri.parse(firstCover.getFileUri()),
                        targetFolder.getUri(),
                        firstCover.getMimeType(),
                        firstCover.getName() + "." + extension);
                if (newUri != null)
                    coverPic.setFileUri(newUri.toString());
                else
                    Timber.w("Could not move file %s", firstCover.getFileUri());
            }
            mergedImages.add(coverPic);

            // Merge images and chapters
            int chapterOrder = 0;
            int pictureOrder = 1;
            int nbProcessedPics = 1;
            Chapter newChapter;
            for (Content c : contentList) {
                if (null == c.getImageFiles()) continue;
                newChapter = null;
                // Create a default "content chapter" that represents the original book before merging
                Chapter contentChapter = new Chapter(chapterOrder++, c.getGalleryUrl(), c.getTitle());
                contentChapter.setUniqueId(c.getUniqueSiteId() + "-" + contentChapter.getOrder());
                for (ImageFile img : c.getImageFiles()) {
                    if (!img.isReadable()) continue;
                    ImageFile newImg = new ImageFile(img);
                    newImg.setId(0); // Force working on a new picture
                    newImg.getContent().setTarget(null); // Clear content
                    newImg.setOrder(pictureOrder++);
                    newImg.computeName(nbMaxDigits);
                    Chapter chapLink = img.getLinkedChapter();
                    if (null == chapLink) { // No chapter -> set content chapter
                        newChapter = contentChapter;
                    } else {
                        if (chapLink.getUniqueId().isEmpty()) chapLink.populateUniqueId();
                        if (null == newChapter || !chapLink.getUniqueId().equals(newChapter.getUniqueId()))
                            newChapter = Chapter.fromChapter(chapLink).setOrder(chapterOrder++);
                    }
                    if (!mergedChapters.contains(newChapter)) mergedChapters.add(newChapter);
                    newImg.setChapter(newChapter);

                    // If exists, move the picture to the merged books' folder
                    if (isInLibrary(newImg.getStatus())) {
                        String extension = HttpHelper.getExtensionFromUri(img.getFileUri());
                        Uri newUri = FileHelper.copyFile(
                                context,
                                Uri.parse(img.getFileUri()),
                                targetFolder.getUri(),
                                newImg.getMimeType(),
                                newImg.getName() + "." + extension);
                        if (newUri != null)
                            newImg.setFileUri(newUri.toString());
                        else
                            Timber.w("Could not move file %s", img.getFileUri());
                        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.generic_progress, 0, nbProcessedPics++, 0, (int) nbImages));
                    }
                    mergedImages.add(newImg);
                }
            }
        } catch (
                IOException e) {
            Timber.w(e);
            isError = true;
        }

        if (!isError) {
            mergedContent.setImageFiles(mergedImages);
            mergedContent.setChapters(mergedChapters); // Chapters have to be attached to Content too
            mergedContent.setQtyPages(mergedImages.size() - 1);
            mergedContent.computeSize();

            DocumentFile jsonFile = ContentHelper.createContentJson(context, mergedContent);
            if (jsonFile != null) mergedContent.setJsonUri(jsonFile.getUri().toString());

            // Save new content (incl. non-custom group operations)
            ContentHelper.addContent(context, dao, mergedContent);

            // Merge custom groups and update
            // Merged book can be a member of one custom group only
            Optional<Group> customGroup = Stream.of(contentList).flatMap(c -> Stream.of(c.groupItems)).map(GroupItem::getGroup).withoutNulls().distinct().filter(g -> g.grouping.equals(Grouping.CUSTOM)).findFirst();
            if (customGroup.isPresent())
                GroupHelper.moveContentToCustomGroup(mergedContent, customGroup.get(), dao);
        }

        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.generic_progress, 0, (int) nbImages, 0, (int) nbImages));
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
