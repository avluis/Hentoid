package me.devsaki.hentoid.util;

import static me.devsaki.hentoid.util.file.FileHelperKt.URI_ELEMENTS_SEPARATOR;
import static me.devsaki.hentoid.util.file.FileHelperKt.cleanFileName;
import static me.devsaki.hentoid.util.file.FileHelperKt.copyFile;
import static me.devsaki.hentoid.util.file.FileHelperKt.findFolder;
import static me.devsaki.hentoid.util.file.FileHelperKt.getDocumentFromTreeUriString;
import static me.devsaki.hentoid.util.file.FileHelperKt.getFileFromSingleUriString;
import static me.devsaki.hentoid.util.file.FileHelperKt.getFileNameWithoutExtension;
import static me.devsaki.hentoid.util.file.FileHelperKt.getInputStream;
import static me.devsaki.hentoid.util.file.FileHelperKt.getMimeTypeFromFileName;
import static me.devsaki.hentoid.util.file.FileHelperKt.getOrCreateCacheFolder;
import static me.devsaki.hentoid.util.file.FileHelperKt.getOutputStream;
import static me.devsaki.hentoid.util.file.FileHelperKt.legacyFileFromUri;
import static me.devsaki.hentoid.util.file.FileHelperKt.listFiles;
import static me.devsaki.hentoid.util.file.FileHelperKt.listFoldersFilter;
import static me.devsaki.hentoid.util.file.FileHelperKt.removeFile;
import static me.devsaki.hentoid.util.image.ImageHelperKt.MIME_IMAGE_GENERIC;
import static me.devsaki.hentoid.util.network.HttpHelperKt.HEADER_CONTENT_TYPE;
import static me.devsaki.hentoid.util.network.HttpHelperKt.HEADER_COOKIE_KEY;
import static me.devsaki.hentoid.util.network.HttpHelperKt.HEADER_REFERER_KEY;
import static me.devsaki.hentoid.util.network.HttpHelperKt.HEADER_USER_AGENT;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kotlin.Pair;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ReaderActivity;
import me.devsaki.hentoid.activities.UnlockActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.DBHelper;
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
import me.devsaki.hentoid.enums.StorageLocation;
import me.devsaki.hentoid.events.DownloadCommandEvent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.util.download.DownloadHelperKt;
import me.devsaki.hentoid.util.exception.ContentNotProcessedException;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.FileNotProcessedException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.file.ArchiveEntry;
import me.devsaki.hentoid.util.file.ArchiveHelperKt;
import me.devsaki.hentoid.util.file.FileExplorer;
import me.devsaki.hentoid.util.file.NameFilter;
import me.devsaki.hentoid.util.image.ImageHelperKt;
import me.devsaki.hentoid.util.network.CloudflareHelper;
import me.devsaki.hentoid.util.network.HttpHelperKt;
import me.devsaki.hentoid.util.network.WebkitPackageHelper;
import me.devsaki.hentoid.util.string_similarity.Cosine;
import me.devsaki.hentoid.util.string_similarity.StringSimilarity;
import me.devsaki.hentoid.workers.PurgeWorker;
import me.devsaki.hentoid.workers.data.DeleteData;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pl.droidsonroids.jspoon.HtmlAdapter;
import pl.droidsonroids.jspoon.Jspoon;
import timber.log.Timber;

/**
 * Utility class for Content-related operations
 */
public final class ContentHelper {

    // == Used for queue management

    @IntDef({QueuePosition.TOP, QueuePosition.BOTTOM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface QueuePosition {
        int TOP = Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_TOP;
        int BOTTOM = Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM;
    }

    // == Used for advanced search
    // NB : Needs to be in sync with the dropdown lists on the advanced search screen

    @IntDef({Location.ANY, Location.PRIMARY, Location.PRIMARY_1, Location.PRIMARY_2, Location.EXTERNAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Location {
        int ANY = 0;
        int PRIMARY = 1; // Primary library - Any location
        int PRIMARY_1 = 2; // Primary library - Location 1
        int PRIMARY_2 = 3; // Primary library - Location 2
        int EXTERNAL = 4; // External library
    }

    @IntDef({Type.ANY, Type.FOLDER, Type.STREAMED, Type.ARCHIVE, Type.PLACEHOLDER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
        int ANY = 0;
        int FOLDER = 1; // Images in a folder
        int STREAMED = 2; // Streamed book
        int ARCHIVE = 3; // Archive
        int PLACEHOLDER = 4; // "Empty book" placeholder created my metadata import
    }


    public static final String KEY_DL_PARAMS_NB_CHAPTERS = "nbChapters";
    public static final String KEY_DL_PARAMS_UGOIRA_FRAMES = "ugo_frames";

    private static final String UNAUTHORIZED_CHARS = "[^a-zA-Z0-9.-]";
    private static final int[] libraryStatus = new int[]{StatusContent.DOWNLOADED.getCode(), StatusContent.MIGRATED.getCode(), StatusContent.EXTERNAL.getCode(), StatusContent.PLACEHOLDER.getCode()};
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

    public static boolean canBeArchived(@NonNull final Content content) {
        return !(content.isArchive() || content.getDownloadMode() == Content.DownloadMode.STREAM || content.getStatus() == StatusContent.PLACEHOLDER);
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
        if (!content.getSite().isVisible()) return; // Support is dropped

        if (!WebkitPackageHelper.INSTANCE.getWebViewAvailable()) {
            if (WebkitPackageHelper.INSTANCE.getWebViewUpdating())
                ToastHelperKt.toast(context, R.string.error_updating_webview);
            else ToastHelperKt.toast(context, R.string.error_missing_webview);
            return;
        }

        Intent intent = new Intent(context, Content.getWebActivityClass(content.getSite()));
        BaseWebActivityBundle bundle = new BaseWebActivityBundle();
        bundle.setUrl(content.getGalleryUrl());
        intent.putExtras(bundle.getBundle());
        if (wrapPin) intent = UnlockActivity.Companion.wrapIntent(context, intent);
        context.startActivity(intent);
    }

    /**
     * Update the given Content's JSON file with its current values
     *
     * @param context Context to use for the action
     * @param content Content whose JSON file to update
     */
    public static boolean updateJson(@NonNull Context context, @NonNull Content content) {
        Helper.assertNonUiThread();

        DocumentFile file = getFileFromSingleUriString(context, content.getJsonUri());
        if (file != null) {
            try (OutputStream output = getOutputStream(context, file)) {
                if (output != null) {
                    JsonHelper.updateJson(JsonContent.fromEntity(content), JsonContent.class, output);
                    return true;
                } else Timber.w("JSON file creation failed for %s", file.getUri());
            } catch (IOException e) {
                Timber.e(e, "Error while writing to %s", content.getJsonUri());
            }
        } else {
            Timber.w("%s does not refer to a valid file", content.getJsonUri());
        }
        return false;
    }

    /**
     * Create the given Content's JSON file and populate it with its current values
     *
     * @param context Context to use
     * @param content Content whose JSON file to create
     * @return Created JSON file, or null if it couldn't be created
     */
    @Nullable
    public static DocumentFile createJson(@NonNull Context context, @NonNull Content content) {
        Helper.assertNonUiThread();
        if (content.isArchive())
            return null; // Keep that as is, we can't find the parent folder anyway

        DocumentFile folder = getDocumentFromTreeUriString(context, content.getStorageUri());
        if (null == folder) return null;
        try {
            DocumentFile newJson = JsonHelper.jsonToFile(context, JsonContent.fromEntity(content), JsonContent.class, folder, Consts.JSON_FILE_NAME_V2);
            content.setJsonUri(newJson.getUri().toString());
            return newJson;
        } catch (IOException e) {
            Timber.e(e, "Error while writing to %s", content.getStorageUri());
        }
        return null;
    }

    /**
     * Persist the given content's JSON file, whether it already exists or it needs to be created
     *
     * @param context Context to use
     * @param content Content to persist the JSON for
     */
    public static void persistJson(@NonNull Context context, @NonNull Content content) {
        boolean result = false;
        if (!content.getJsonUri().isEmpty()) result = ContentHelper.updateJson(context, content);
        if (!result) ContentHelper.createJson(context, content);
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
        List<Content> errors = dao.selectErrorContent();

        // Save current queue (to be able to restore it in case the app gets uninstalled)
        List<Content> queuedContent = Stream.of(queue).map(qr -> {
            Content c = qr.getContent().getTarget();
            if (c != null) c.setFrozen(qr.isFrozen());
            return c;
        }).withoutNulls().toList();
        if (errors != null) queuedContent.addAll(errors);

        DocumentFile rootFolder = getDocumentFromTreeUriString(context, Preferences.getStorageUri(StorageLocation.PRIMARY_1));
        if (null == rootFolder) return false;

        try {
            JsonContentCollection contentCollection = new JsonContentCollection();
            contentCollection.setQueue(queuedContent);

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
     * @param context          Context to use for the action
     * @param content          Content to view
     * @param pageNumber       Page number to view
     * @param searchParams     Current search parameters (so that the next/previous book feature
     *                         is faithful to the library screen's order)
     * @param forceShowGallery True to force the gallery screen to show first; false to follow app settings
     * @param newTask          True to open the reader as a new Task
     */
    public static boolean openReader(@NonNull Context context, @NonNull Content content, int pageNumber, Bundle searchParams, boolean forceShowGallery, boolean newTask) {
        // Check if the book has at least its own folder
        if (content.getStorageUri().isEmpty()) return false;
        if (content.getStatus().equals(StatusContent.PLACEHOLDER)) return false;

        Timber.d("Opening: %s from: %s", content.getTitle(), content.getStorageUri());

        ReaderActivityBundle builder = new ReaderActivityBundle();
        builder.setContentId(content.getId());
        if (searchParams != null) builder.setSearchParams(searchParams);
        if (pageNumber > -1) builder.setPageNumber(pageNumber);
        builder.setForceShowGallery(forceShowGallery);

        Intent intent = new Intent(context, newTask ? ReaderActivity.ReaderActivityMulti.class : ReaderActivity.class);
        intent.putExtras(builder.getBundle());

        if (newTask) intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
        return true;
    }

    /**
     * Update the given Content read stats in both DB and JSON file
     *
     * @param context                 Context to use for the action
     * @param dao                     DAO to use for the action
     * @param content                 Content to update
     * @param images                  Images to attach to the given Content
     * @param targetLastReadPageIndex Index of the last read page
     * @param updateReads             True to increment the reads counter and to set the last read date to now
     * @param markAsCompleted         True to mark as completed
     */
    public static void updateContentReadStats(@NonNull Context context, @NonNull CollectionDAO dao, @NonNull Content content, @NonNull List<ImageFile> images, int targetLastReadPageIndex, boolean updateReads, boolean markAsCompleted) {
        content.setLastReadPageIndex(targetLastReadPageIndex);
        if (updateReads) content.increaseReads().setLastReadDate(Instant.now().toEpochMilli());
        if (markAsCompleted) content.setCompleted(true);
        dao.replaceImageList(content.getId(), images);
        dao.insertContentCore(content);
        persistJson(context, content);
    }

    /**
     * Find the picture files for the given Content
     * NB1 : Pictures with non-supported formats are not included in the results
     * NB2 : Cover picture is not included in the results
     *
     * @param context Context to use
     * @param content Content to retrieve picture files for
     * @return List of picture files
     */
    public static List<DocumentFile> getPictureFilesFromContent(@NonNull final Context context, @NonNull final Content content) {
        Helper.assertNonUiThread();
        String storageUri = content.getStorageUri();

        Timber.d("Opening: %s from: %s", content.getTitle(), storageUri);
        DocumentFile folder = getDocumentFromTreeUriString(context, storageUri);
        if (null == folder) {
            Timber.d("File not found!! Exiting method.");
            return new ArrayList<>();
        }

        return listFoldersFilter(context, folder, displayName -> (displayName.toLowerCase().startsWith(Consts.THUMB_FILE_NAME) && ImageHelperKt.isSupportedImage(displayName)));
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
            DocumentFile archive = getFileFromSingleUriString(context, content.getStorageUri());
            if (null == archive)
                throw new FileNotProcessedException(content, "Failed to find archive " + content.getStorageUri());

            if (archive.delete()) {
                Timber.i("Archive removed : %s", content.getStorageUri());
            } else {
                throw new FileNotProcessedException(content, "Failed to delete archive " + content.getStorageUri());
            }

            // Remove the cover stored in the app's persistent folder
            File appFolder = context.getFilesDir();
            File[] images = appFolder.listFiles((dir, name) -> getFileNameWithoutExtension(name).equals(content.getId() + ""));
            if (images != null) for (File f : images) removeFile(f);
        } else if (/*isInLibrary(content.getStatus()) &&*/ !content.getStorageUri().isEmpty()) { // Remove a folder and its content
            // If the book has just starting being downloaded and there are no complete pictures on memory yet, it has no storage folder => nothing to delete
            DocumentFile folder = getDocumentFromTreeUriString(context, content.getStorageUri());
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
                EventBus.getDefault().post(new DownloadCommandEvent(DownloadCommandEvent.Type.EV_CANCEL, content));

            // Remove from queue
            dao.deleteQueue(content);
        }

        // Remove content itself
        if (deleteContent) removeContent(context, dao, content);
    }

    /**
     * Remove all external content from DB without removing files (=detach)
     *
     * @param context Context to use
     * @param dao     DAO to use
     */
    public static void detachAllExternalContent(@NonNull final Context context, @NonNull final CollectionDAO dao) {
        // Remove all external books from DB
        // NB : do NOT use ContentHelper.removeContent as it would remove files too
        // here we just want to remove DB entries without removing files
        dao.deleteAllExternalBooks();

        // Remove all images stored in the app's persistent folder (archive covers)
        File appFolder = context.getFilesDir();
        File[] images = appFolder.listFiles((file, s) -> ImageHelperKt.isSupportedImage(s));
        if (images != null) for (File f : images) removeFile(f);
    }

    /**
     * Remove all content from the given primary location from DB without removing files (=detach)
     *
     * @param dao      DAO to use
     * @param location Location to detach
     */
    public static void detachAllPrimaryContent(@NonNull final CollectionDAO dao, StorageLocation location) {
        // Remove all external books from DB
        // NB : do NOT use ContentHelper.removeContent as it would remove files too
        // here we just want to remove DB entries without removing files
        dao.deleteAllInternalBooks(getPathRoot(location), true);

        // TODO groups
    }

    public static String getPathRoot(StorageLocation location) {
        return getPathRoot(Preferences.getStorageUri(location));
    }

    public static String getPathRoot(String locationUriStr) {
        int pathDivider = locationUriStr.lastIndexOf(URI_ELEMENTS_SEPARATOR);
        if (pathDivider > -1)
            return locationUriStr.substring(0, pathDivider + URI_ELEMENTS_SEPARATOR.length()); // Include separator
        return locationUriStr;
    }

    /**
     * Add new content to the library
     *
     * @param context Context to use
     * @param dao     DAO to use
     * @param content Content to add to the library
     * @return ID of the newly added Content
     */
    public static long addContent(@NonNull final Context context, @NonNull final CollectionDAO dao, @NonNull final Content content) {
        Helper.assertNonUiThread();
        long newContentId = dao.insertContent(content);
        content.setId(newContentId);

        // Perform group operations only if
        //   - the book is in the library (i.e. not queued)
        //   - the book is linked to no group from the given grouping
        if (Helper.getListFromPrimitiveArray(libraryStatus).contains(content.getStatus().getCode())) {
            List<Grouping> staticGroupings = Stream.of(Grouping.getEntries()).filter(Grouping::getCanReorderBooks).toList();
            for (Grouping g : staticGroupings)
                if (content.getGroupItems(g).isEmpty()) {
                    if (g.equals(Grouping.ARTIST)) {
                        int nbGroups = (int) dao.countGroupsFor(g);
                        AttributeMap attrs = content.getAttributeMap();
                        List<Attribute> artists = new ArrayList<>();
                        Set<Attribute> sublist = attrs.get(AttributeType.ARTIST);
                        if (sublist != null) artists.addAll(sublist);
                        sublist = attrs.get(AttributeType.CIRCLE);
                        if (sublist != null) artists.addAll(sublist);

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
                                GroupHelper.addContentToAttributeGroup(group, a, content, dao);
                            }
                        }
                    }
                }
        }

        // Extract the cover to the app's persistent folder if the book is an archive
        if (content.isArchive() && content.getImageFiles() != null) {
            DocumentFile archive = getFileFromSingleUriString(context, content.getStorageUri());
            if (archive != null) {
                try {
                    File targetFolder = context.getFilesDir();
                    List<Pair<String, String>> extractInstructions = new ArrayList<>();
                    extractInstructions.add(new Pair<>(content.getCover().getFileUri().replace(content.getStorageUri() + File.separator, ""), newContentId + ""));
                    List<Uri> results = ArchiveHelperKt.extractArchiveEntriesSimple(context, archive.getUri(), targetFolder, extractInstructions);
                    if (!results.isEmpty()) {
                        Uri uri = results.get(0);

                        // Save the pic as low-res JPG
                        File extractedFile = new File(uri.getPath()); // These are file URI's
                        if (extractedFile.length() > 0) {
                            try (InputStream is = getInputStream(context, uri)) {
                                Bitmap b = BitmapFactory.decodeStream(is);
                                if (b != null) {
                                    String targetFileName = Consts.EXT_THUMB_FILE_PREFIX + extractedFile.getName();
                                    // Reuse existing file if exists
                                    File finalFile;
                                    File[] existingFiles = targetFolder.listFiles((file, s) -> s.equals(targetFileName));
                                    if (existingFiles != null && existingFiles.length > 0) {
                                        finalFile = existingFiles[0];
                                    } else { // Create new file
                                        finalFile = new File(targetFolder, targetFileName);
                                    }
                                    try (OutputStream os = getOutputStream(finalFile)) {
                                        Bitmap resizedBitmap = ImageHelperKt.getScaledDownBitmap(b, Helper.dimensAsPx(context, Settings.INSTANCE.getLibraryGridCardWidthDP()), false);
                                        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, os);
                                        resizedBitmap.recycle();
                                    }
                                    uri = Uri.fromFile(finalFile);
                                }

                            } finally {
                                if (!extractedFile.delete())
                                    Timber.w("Failed deleting file %s", extractedFile.getAbsolutePath());
                            }
                            Timber.i(">> Set cover for %s", content.getTitle());
                            content.getCover().setFileUri(uri.toString());
                            content.getCover().setName(uri.getLastPathSegment());
                            dao.replaceImageList(newContentId, content.getImageFiles());
                        }
                    }
                } catch (IOException e) {
                    Timber.w(e);
                }
            }
        }

        return newContentId;
    }

    /**
     * Add a new Attribute to the library master data (and updates groups accordingly)
     *
     * @param type Attribute type of the new Attribute
     * @param name Name of the new Attribute
     * @param dao  DAO to use
     * @return Newly created Attribute
     */
    public static Attribute addAttribute(@NonNull final AttributeType type,
                                         @NonNull final String name, @NonNull final CollectionDAO dao) {
        Group artistGroup = null;
        if (type.equals(AttributeType.ARTIST) || type.equals(AttributeType.CIRCLE))
            artistGroup = GroupHelper.addArtistToAttributesGroup(name, dao);
        Attribute attr = new Attribute(type, name);
        long newId = dao.insertAttribute(attr);
        attr.setId(newId);
        if (artistGroup != null) attr.putGroup(artistGroup);
        return attr;
    }

    /**
     * Remove the given pages from the disk and the DB
     *
     * @param images  Pages to be removed
     * @param dao     DAO to be used
     * @param context Context to be used
     */
    public static void removePages(@NonNull List<ImageFile> images, @NonNull CollectionDAO
            dao, @NonNull final Context context) {
        Helper.assertNonUiThread();
        // Remove from DB
        // NB : start with DB to have a LiveData feedback, because file removal can take much time
        dao.deleteImageFiles(images);

        // Remove the pages from disk
        for (ImageFile image : images)
            removeFile(context, Uri.parse(image.getFileUri()));

        // Lists all relevant content
        List<Long> contents = Stream.of(images).filter(i -> i.getContent() != null).map(i -> i.getContent().getTargetId()).distinct().toList();

        // Update content JSON if it exists (i.e. if book is not queued)
        for (Long contentId : contents) {
            Content content = dao.selectContent(contentId);
            if (content != null && !content.getJsonUri().isEmpty())
                updateJson(context, content);
        }
    }

    /**
     * Set one of the given Content's ImageFile as the Content's cover and persist that new setting to the DB
     *
     * @param newCover ImageFile to be used as a cover for the Content it is related to
     * @param dao      DAO to be used
     * @param context  Context to be used
     */
    public static void setAndSaveContentCover(@NonNull ImageFile
                                                      newCover, @NonNull CollectionDAO dao, @NonNull final Context context) {
        Helper.assertNonUiThread();

        // Get all images from the DB
        Content content = dao.selectContent(newCover.getContent().getTargetId());
        if (null == content) return;
        List<ImageFile> images = content.getImageFiles();
        if (null == images) return;

        // Remove current cover from the set
        setContentCover(content, images, newCover);

        // Update images directly
        dao.insertImageFiles(images);

        // Update the whole list
        dao.insertContent(content);

        // Update content JSON if it exists (i.e. if book is not queued)
        if (!content.getJsonUri().isEmpty()) updateJson(context, content);
    }

    /**
     * Set one of the given Content's ImageFile as the Content's cover
     * NB : That method doesn't persist the result state to the DB
     *
     * @param content  Content to set the new cover for
     * @param images   Images of the given Content
     * @param newCover ImageFile to be used as a cover for the Content it is related to
     */
    public static void setContentCover(@NonNull Content
                                               content, @NonNull List<ImageFile> images, @NonNull ImageFile newCover) {
        // Remove current cover from the set
        for (int i = 0; i < images.size(); i++)
            if (images.get(i).isCover()) {
                if (images.get(i).isReadable()) images.get(i).setIsCover(false);
                else images.remove(i);
                break;
            }

        // Duplicate given picture and set it as a cover
        ImageFile cover = ImageFile.newCover(newCover.getUrl(), newCover.getStatus()).setFileUri(newCover.getFileUri()).setMimeType(newCover.getMimeType());
        images.add(0, cover);

        // Update cover URL to "ping" the content to be updated too (useful for library screen that only detects "direct" content updates)
        content.setCoverImageUrl(newCover.getUrl());
    }

    /**
     * Create the download directory of the given content
     *
     * @param context    Context to use
     * @param content    Content for which the directory to create
     * @param createOnly Set to true to exclusively create a new folder; set to false if one can reuse an existing folder
     * @return Created or existing directory
     */
    @Nullable
    public static DocumentFile getOrCreateContentDownloadDir(@NonNull Context
                                                                     context, @NonNull Content content, @NonNull StorageLocation location, boolean createOnly) {
        // == Site folder
        DocumentFile siteDownloadDir = getOrCreateSiteDownloadDir(context, location, content.getSite());
        if (null == siteDownloadDir) return null;

        // == Book folder
        Pair<String, String> bookFolderName = formatBookFolderName(content);

        // First try finding the folder with new naming...
        if (!createOnly) {
            DocumentFile bookFolder = findFolder(context, siteDownloadDir, bookFolderName.getFirst());
            if (null == bookFolder) { // ...then with old (sanitized) naming
                bookFolder = findFolder(context, siteDownloadDir, bookFolderName.getSecond());
            }
            if (bookFolder != null) return bookFolder;
        }

        // If nothing found, or create-only, create a new folder with the new naming...
        DocumentFile result = siteDownloadDir.createDirectory(bookFolderName.getFirst());
        if (null == result) { // ...if it fails, create a new folder with the old naming
            return siteDownloadDir.createDirectory(bookFolderName.getSecond());
        } else return result;
    }

    /**
     * Format the download directory path of the given content according to current user preferences
     *
     * @param content Content to get the path from
     * @return Pair containing the canonical naming of the given content :
     * - Left side : Naming convention allowing non-ANSI characters
     * - Right side : Old naming convention with ANSI characters alone
     */
    public static Pair<String, String> formatBookFolderName(
            @NonNull final Content content) {
        String title = content.getTitle();
        title = (null == title) ? "" : title;
        String author = formatBookAuthor(content).toLowerCase();

        return new Pair<>(formatBookFolderName(content, cleanFileName(title), cleanFileName(author)), formatBookFolderName(content, title.replaceAll(UNAUTHORIZED_CHARS, "_"), author.replaceAll(UNAUTHORIZED_CHARS, "_")));
    }

    private static String formatBookFolderName(@NonNull final Content content,
                                               @NonNull final String title, @NonNull final String author) {
        String result = "";
        switch (Preferences.getFolderNameFormat()) {
            case Preferences.Constant.FOLDER_NAMING_CONTENT_TITLE_ID -> result += title;
            case Preferences.Constant.FOLDER_NAMING_CONTENT_AUTH_TITLE_ID ->
                    result += author + " - " + title;
            case Preferences.Constant.FOLDER_NAMING_CONTENT_TITLE_AUTH_ID ->
                    result += title + " - " + author;
            default -> {
                // Nothing to do
            }
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
        content.populateUniqueSiteId();
        String id = content.getUniqueSiteId();
        // For certain sources (8muses, fakku), unique IDs are strings that may be very long
        // => shorten them by using their hashCode
        if (id.length() > 10) id = StringHelper.formatIntAsStr(Math.abs(id.hashCode()), 10);
        return "[" + id + "]";
    }

    /**
     * Format the given Content's artists and circles to form the "author" string
     *
     * @param content Content to use
     * @return Resulting author string
     */
    public static String formatBookAuthor(@NonNull final Content content) {
        String result = "";
        AttributeMap attrMap = content.getAttributeMap();
        // Try and get first Artist
        Set<Attribute> artistAttributes = attrMap.get(AttributeType.ARTIST);
        if (artistAttributes != null && !artistAttributes.isEmpty()) {
            Optional<Attribute> attr = Stream.of(artistAttributes).findFirst();
            if (attr.isPresent()) result = attr.get().getName();
        }

        // If no Artist found, try and get first Circle
        if (null == result || result.isEmpty()) {
            Set<Attribute> circleAttributes = attrMap.get(AttributeType.CIRCLE);
            if (circleAttributes != null && !circleAttributes.isEmpty()) {
                Optional<Attribute> attr = Stream.of(circleAttributes).findFirst();
                if (attr.isPresent()) result = attr.get().getName();
            }
        }

        return StringHelper.protect(result);
    }

    /**
     * Return the given site's download directory. Create it if it doesn't exist.
     * <p>
     * Avoid overloading the Android folder structure (not designed for that :/)
     * by preventing any site folder from storing more than 250 books/subfolders
     * => create a new "siteN" folder when needed (e.g. nhentai1, nhentai2, nhentai3...)
     *
     * @param context  Context to use for the action
     * @param location Location to get/create the folder in
     * @param site     Site to get the download directory for
     * @return Download directory of the given Site
     */
    @Nullable
    public static DocumentFile getOrCreateSiteDownloadDir(
            @NonNull Context context,
            @NonNull StorageLocation location,
            @NonNull Site site) {
        String appUriStr = Preferences.getStorageUri(location);
        if (appUriStr.isEmpty()) {
            Timber.e("No storage URI defined for location %s", location.name());
            return null;
        }
        DocumentFile appFolder = getDocumentFromTreeUriString(context, appUriStr);
        if (null == appFolder) {
            Timber.e("App folder %s does not exist", appUriStr);
            return null;
        }

        try (FileExplorer explorer = new FileExplorer(context, appFolder)) {
            String siteFolderName = site.getFolder();
            List<DocumentFile> siteFolders = explorer.listDocumentFiles(context, appFolder, displayName -> displayName.startsWith(siteFolderName), true, false, false);
            // Order by name (nhentai, nhentai1, ..., nhentai10)
            siteFolders = Stream.of(siteFolders).withoutNulls().sorted(new InnerNameNumberFileComparator()).toList();

            if (siteFolders.isEmpty()) // Create
                return appFolder.createDirectory(siteFolderName);
            else {
                // Check number of subfolders
                for (DocumentFile siteFolder : siteFolders) {
                    int nbSubfolders = explorer.countFolders(siteFolder, displayName -> true);
                    if (nbSubfolders < 250) return siteFolder;
                }

                // Create new one with the next number (taken from the name of the last folder itself, to handle cases where numbering is not contiguous)
                int newDigits = siteFolders.size();
                String lastDigits = StringHelper.keepDigits(StringHelper.protect(siteFolders.get(siteFolders.size() - 1).getName()).toLowerCase().replace(site.getFolder().toLowerCase(), ""));
                if (!lastDigits.isEmpty()) newDigits = Integer.parseInt(lastDigits) + 1;
                return appFolder.createDirectory(siteFolderName + newDigits);
            }
        } catch (IOException e) {
            Timber.w(e);
        }
        return null;
    }

    /**
     * Open the "share with..." Android dialog for the given list of Content
     *
     * @param context Context to use for the action
     * @param items   List of Content to share
     */
    public static void shareContent(@NonNull final Context context,
                                    @NonNull final List<Content> items) {
        if (items.isEmpty()) return;

        String subject = (1 == items.size()) ? items.get(0).getTitle() : "";
        String text = StringUtils.join(Stream.of(items).map(Content::getGalleryUrl).toList(), System.getProperty("line.separator"));

        Helper.shareText(context, subject, text);
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
            if ('.' == s.charAt(i))
                return (-1 == beginIndex) ? "0" : s.substring(beginIndex, i);
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
    public static List<ImageFile> matchFilesToImageList(
            @NonNull final List<DocumentFile> files, @NonNull final List<ImageFile> images) {
        Map<String, Pair<String, Long>> fileNameProperties = new HashMap<>(files.size());
        List<ImageFile> result = new ArrayList<>();
        boolean coverFound = false;

        // Put file names into a Map to speed up the lookup
        for (DocumentFile file : files)
            if (file.getName() != null)
                fileNameProperties.put(removeLeadingZeroesAndExtensionCached(file.getName()), new Pair<>(file.getUri().toString(), file.length()));

        // Look up similar names between images and file names
        int order;
        int previousOrder = -1;
        List<ImageFile> orderedImages = Stream.of(images).sortBy(ImageFile::getOrder).toList();
        for (int i = 0; i < orderedImages.size(); i++) {
            ImageFile img = orderedImages.get(i);
            String imgName = removeLeadingZeroesAndExtensionCached(img.getName());

            Pair<String, Long> property;
            boolean isOnline = img.getStatus().equals(StatusContent.ONLINE);
            if (isOnline) {
                property = new Pair<>("", 0L);
            } else {
                // Detect gaps inside image numbering
                order = img.getOrder();
                // Look for files named with the forgotten number
                if (previousOrder > -1 && previousOrder < order - 1) {
                    Timber.i("Numbering gap detected : %d to %d", previousOrder, order);
                    for (int j = previousOrder + 1; j < order; j++) {
                        Pair<String, Long> localProperty = fileNameProperties.get(j + "");
                        if (localProperty != null) {
                            Timber.i("Numbering gap filled with a file : %d", j);
                            ImageFile newImage = ImageFile.fromImageUrl(j, orderedImages.get(i - 1).getUrl(), StatusContent.DOWNLOADED, orderedImages.size());
                            newImage.setFileUri(localProperty.getFirst()).setSize(localProperty.getSecond());
                            result.add(Math.max(0, result.size() - 1), newImage);
                        }
                    }
                }
                previousOrder = order;

                property = fileNameProperties.get(imgName);
            }
            if (property != null) {
                if (imgName.startsWith(Consts.THUMB_FILE_NAME)) {
                    coverFound = true;
                    img.setIsCover(true);
                }
                result.add(img.setFileUri(property.getFirst()).setSize(property.getSecond()).setStatus(isOnline ? StatusContent.ONLINE : StatusContent.DOWNLOADED));
            } else Timber.i(">> image not found among files : %s", imgName);
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
    public static List<ImageFile> createImageListFromFolder(@NonNull final Context context,
                                                            @NonNull final DocumentFile folder) {
        List<DocumentFile> imageFiles = listFiles(context, folder, ImageHelperKt.getImageNamesFilter());
        if (!imageFiles.isEmpty()) return createImageListFromFiles(imageFiles);
        else return Collections.emptyList();
    }

    /**
     * Create the list of ImageFiles from the given files
     *
     * @param files Files to find images into
     * @return List of ImageFiles corresponding to all supported pictures among the given files, sorted numerically then alphabetically
     */
    public static List<ImageFile> createImageListFromFiles(
            @NonNull final List<DocumentFile> files) {
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
            @NonNull final List<DocumentFile> files, @NonNull final StatusContent targetStatus,
            int startingOrder, @NonNull final String namePrefix) {
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
            img.setName(getFileNameWithoutExtension(name)).setOrder(order).setUrl(f.getUri().toString()).setStatus(targetStatus).setFileUri(f.getUri().toString()).setSize(f.length());
            img.setMimeType(getMimeTypeFromFileName(name));
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
            @NonNull final Uri archiveFileUri, @NonNull final List<ArchiveEntry> files,
            @NonNull final StatusContent targetStatus, int startingOrder,
            @NonNull final String namePrefix) {
        Helper.assertNonUiThread();
        List<ImageFile> result = new ArrayList<>();
        int order = startingOrder;
        // Sort files by anything that resembles a number inside their names (default entry order from ZipInputStream is chaotic)
        List<ArchiveEntry> fileList = Stream.of(files).withoutNulls().sorted(new InnerNameNumberArchiveComparator()).toList();
        for (ArchiveEntry f : fileList) {
            String name = namePrefix + f.getPath();
            String path = archiveFileUri + File.separator + f.getPath();
            ImageFile img = new ImageFile();
            if (name.startsWith(Consts.THUMB_FILE_NAME)) img.setIsCover(true);
            else order++;
            img.setName(getFileNameWithoutExtension(name)).setOrder(order).setUrl(path).setStatus(targetStatus).setFileUri(path).setSize(f.getSize());
            img.setMimeType(getMimeTypeFromFileName(name));
            result.add(img);
        }
        return result;
    }

    /**
     * Launch the web browser for the given site and URL
     *
     * @param context   Context to be used
     * @param targetUrl Url to navigate to
     */
    public static void launchBrowserFor(@NonNull final Context context,
                                        @NonNull final String targetUrl) {
        if (!WebkitPackageHelper.INSTANCE.getWebViewAvailable()) {
            if (WebkitPackageHelper.INSTANCE.getWebViewUpdating())
                ToastHelperKt.toast(context, R.string.error_updating_webview);
            else ToastHelperKt.toast(context, R.string.error_missing_webview);
            return;
        }
        Site targetSite = Site.searchByUrl(targetUrl);
        if (null == targetSite || targetSite.equals(Site.NONE)) return;

        Intent intent = new Intent(context, Content.getWebActivityClass(targetSite));

        BaseWebActivityBundle bundle = new BaseWebActivityBundle();
        bundle.setUrl(targetUrl);
        intent.putExtras(bundle.getBundle());

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
            List<String> tags = Stream.of(content.getAttributes()).filter(a -> a.getType().equals(AttributeType.TAG) || a.getType().equals(AttributeType.LANGUAGE)).map(Attribute::getName).toList();
            for (String blocked : Preferences.getBlockedTags())
                for (String tag : tags)
                    if (blocked.equalsIgnoreCase(tag) || StringHelper.isPresentAsWord(blocked, tag)) {
                        if (result.isEmpty()) result = new ArrayList<>();
                        result.add(tag);
                        break;
                    }
            if (tags.size() > 0 && tags.size() == result.size())
                AchievementsManager.INSTANCE.trigger(2);
        }
        return result;
    }

    /**
     * Update the given content's properties by parsing its webpage
     *
     * @param content Content to parse again from its online source
     * @return Content updated from its online source, or Optional.empty if something went wrong
     */
    public static Optional<Content> reparseFromScratch(@NonNull final Content content) {
        try {
            return reparseFromScratch(content.getGalleryUrl(), content);
        } catch (IOException | CloudflareHelper.CloudflareProtectedException e) {
            Timber.w(e);
            return Optional.empty();
        }
    }

    /**
     * Create a new Content by parsing the webpage at the given URL
     *
     * @param url Webpage to parse to create the Content
     * @return Content created from the webpage at the given URL, or Optional.empty if something went wrong
     * @throws IOException If something horrible happens during parsing
     */
    public static Optional<Content> parseFromScratch(@NonNull final String url) throws IOException, CloudflareHelper.CloudflareProtectedException {
        return reparseFromScratch(url, null);
    }

    /**
     * Parse the given webpage to update the given Content's properties
     *
     * @param url     Webpage to parse to update the given Content's properties
     * @param content Content which properties to update (read-only). If this parameter is null, the call returns a completely new Content
     * @return Content with updated properties, or Optional.empty if something went wrong
     * @throws IOException If something horrible happens during parsing
     */
    private static Optional<Content> reparseFromScratch(
            @NonNull final String url,
            @Nullable final Content content) throws IOException, CloudflareHelper.CloudflareProtectedException {
        Helper.assertNonUiThread();

        String urlToLoad;
        Site site;
        if (null == content) {
            urlToLoad = url;
            site = Site.searchByUrl(url);
        } else {
            urlToLoad = content.getGalleryUrl();
            site = content.getSite();
        }
        if (null == site || Site.NONE == site) return Optional.empty();

        Pair<ResponseBody, String> fetchResponse = fetchBodyFast(urlToLoad, site, null, "text/html");
        try (ResponseBody body = fetchResponse.getFirst()) {
            if (null == body) return Optional.empty();

            Class<? extends ContentParser> c = ContentParserFactory.INSTANCE.getContentParserClass(site);
            final Jspoon jspoon = Jspoon.create();
            HtmlAdapter<? extends ContentParser> htmlAdapter = jspoon.adapter(c); // Unchecked but alright

            ContentParser contentParser = htmlAdapter.fromInputStream(body.byteStream(), new URL(urlToLoad));
            Content newContent;
            if (null == content) newContent = contentParser.toContent(urlToLoad);
            else newContent = contentParser.update(content, urlToLoad, true);

            newContent.setJsonUri("");
            newContent.clearStorageDoc();
            newContent.setParentStorageUri("");

            if (newContent.getStatus() != null && newContent.getStatus().equals(StatusContent.IGNORED)) {
                String canonicalUrl = contentParser.getCanonicalUrl();
                if (!canonicalUrl.isEmpty() && !canonicalUrl.equalsIgnoreCase(urlToLoad))
                    return reparseFromScratch(canonicalUrl, content);
                else return Optional.empty();
            }

            // Clear existing chapters to avoid issues with extra chapter detection
            newContent.clearChapters();

            // Save cookies for future calls during download
            Map<String, String> params = new HashMap<>();
            String cookieStr = fetchResponse.getSecond();
            if (!cookieStr.isEmpty()) params.put(HEADER_COOKIE_KEY, cookieStr);

            newContent.setDownloadParams(JsonHelper.serializeToJson(params, JsonHelper.MAP_STRINGS));
            return Optional.of(newContent);
        }
    }

    private static Pair<ResponseBody, String> fetchBodyFast(
            @NonNull String url,
            @NonNull Site site,
            List<Pair<String, String>> requestHeaders,
            String targetContentType) throws IOException, CloudflareHelper.CloudflareProtectedException {

        List<Pair<String, String>> requestHeadersList;
        if (null == requestHeaders) {
            requestHeadersList = new ArrayList<>();
            requestHeadersList.add(new Pair<>(HEADER_REFERER_KEY, url));
        } else {
            requestHeadersList = requestHeaders;
        }
        String cookieStr = HttpHelperKt.getCookies(url, requestHeadersList, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
        if (!cookieStr.isEmpty())
            requestHeadersList.add(new Pair<>(HEADER_COOKIE_KEY, cookieStr));

        Response response = HttpHelperKt.getOnlineResourceFast(url, requestHeadersList, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());

        // Raise exception if blocked by Cloudflare
        if (503 == response.code() && site.isUseCloudflare())
            throw new CloudflareHelper.CloudflareProtectedException();

        // Scram if the response is a redirection or an error
        if (response.code() >= 300)
            throw new IOException("Network error " + response.code() + " @ " + url);

        // Scram if the response content-type is something else than the target type
        if (targetContentType != null) {
            Pair<String, String> contentType = HttpHelperKt.cleanContentType(StringHelper.protect(response.header(HEADER_CONTENT_TYPE, "")));
            if (!contentType.getFirst().isEmpty() && !contentType.getFirst().equalsIgnoreCase(targetContentType))
                throw new IOException("Not an HTML resource " + url);
        }

        return new Pair<>(response.body(), cookieStr);
    }

    /**
     * Query source to fetch all image file names and URLs of a given book
     *
     * @param content           Book whose pages to retrieve
     * @param url               Url from which to parse pages from (e.g. gallery or chapter)
     * @param targetImageStatus Target status to set on the fetched images
     * @return List of pages with original URLs and file name
     */
    public static List<ImageFile> fetchImageURLs(
            @NonNull Content content,
            @NonNull String url,
            @NonNull StatusContent targetImageStatus) throws Exception {
        List<ImageFile> imgs;

        // If content doesn't have any download parameters, get them from the cookie manager
        String contentDownloadParamsStr = content.getDownloadParams();
        if (null == contentDownloadParamsStr || contentDownloadParamsStr.isEmpty()) {
            String cookieStr = HttpHelperKt.getCookies(url);
            if (!cookieStr.isEmpty()) {
                Map<String, String> downloadParams = new HashMap<>();
                downloadParams.put(HEADER_COOKIE_KEY, cookieStr);
                content.setDownloadParams(JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS));
            }
        }

        // Use ImageListParser to query the source
        ImageListParser parser = ContentParserFactory.INSTANCE.getImageListParser(content.getSite());
        imgs = parser.parseImageList(content, url);

        // If no images found, or just the cover, image detection has failed
        if (imgs.isEmpty() || (1 == imgs.size() && imgs.get(0).isCover()))
            throw new EmptyResultException(url);

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
                    if (!imageDownloadParams.containsKey(HEADER_REFERER_KEY))
                        imageDownloadParams.put(HEADER_REFERER_KEY, content.getSite().getUrl());
                    i.setDownloadParams(JsonHelper.serializeToJson(imageDownloadParams, JsonHelper.MAP_STRINGS));
                } else {
                    i.setDownloadParams(contentDownloadParamsStr);
                }
            }
        }

        // Cleanup and enrich generated objects
        for (ImageFile img : imgs) {
            img.setId(0);
            img.setStatus(targetImageStatus);
            img.setContentId(content.getId());
        }

        return imgs;
    }

    /**
     * Remove all files from the given Content's folder.
     * The folder itself is left empty except if JSON and cover are to be kept.
     * <p/>
     * NB : "Thanks to" Android SAF, it is faster to :
     * 1/ copy kept files to temp storage
     * 2/ delete the whole folder and its entire content
     * 3/ re-create it
     * 4/ copy back kept files into it
     * ...rather than to delete all image files one by one.
     * <p/>
     * => code is way more complex but exec time is way faster
     *
     * @param context     Context to use
     * @param content     Content to remove files from
     * @param removeJson  True to remove the Hentoid JSON file; false to keep it
     * @param removeCover True to remove the cover picture; false to keep it
     */
    public static void purgeFiles(@NonNull final Context context,
                                  @NonNull final Content content, boolean removeJson, boolean removeCover) {
        DocumentFile bookFolder = getDocumentFromTreeUriString(context, content.getStorageUri());
        if (bookFolder != null) {
            // Identify files to keep
            NameFilter namesToKeep = displayName -> {
                String name = displayName.toLowerCase();
                return (!removeJson && name.endsWith("json")) || (!removeCover && name.startsWith(Consts.THUMB_FILE_NAME));
            };
            List<DocumentFile> filesToKeep = listFiles(context, bookFolder, namesToKeep);

            // If any, copy them to temp storage
            List<File> tempFiles = new ArrayList<>();
            File tempFolder = null;
            if (!filesToKeep.isEmpty()) {
                tempFolder = getOrCreateCacheFolder(context, "tmp" + content.getId());
                for (DocumentFile file : filesToKeep) {
                    try {
                        Uri uri = copyFile(context, file.getUri(), Uri.fromFile(tempFolder), StringHelper.protect(file.getType()), StringHelper.protect(file.getName()));
                        if (uri != null) {
                            File tmpFile = legacyFileFromUri(uri);
                            if (tmpFile != null) tempFiles.add(tmpFile);
                        }
                    } catch (IOException e) {
                        Timber.w(e);
                    }
                }
            }

            try {
                // Delete the whole initial folder
                bookFolder.delete();

                // Re-create an empty folder with the same name
                DocumentFile siteFolder = ContentHelper.getOrCreateSiteDownloadDir(context, getLocation(content), content.getSite());
                if (siteFolder != null) {
                    String name = bookFolder.getName();
                    if (name != null) bookFolder = siteFolder.createDirectory(name);
                    else
                        bookFolder = getOrCreateContentDownloadDir(context, content, getLocation(content), false);
                }

                if (bookFolder != null) {
                    content.setStorageDoc(bookFolder);
                    // Copy back the files to the new folder
                    for (File file : tempFiles) {
                        try {
                            String name = file.getName().toLowerCase();
                            String mimeType = getMimeTypeFromFileName(name);
                            Uri newUri = copyFile(context, Uri.fromFile(file), bookFolder.getUri(), mimeType, file.getName());
                            if (newUri != null && name.endsWith("json"))
                                content.setJsonUri(newUri.toString());
                        } catch (IOException e) {
                            Timber.w(e);
                        }
                    }
                }
            } finally {
                // Delete temp files
                if (tempFolder != null) tempFolder.delete();
            }
        }
    }

    /**
     * Format the given Content's tags for display
     *
     * @param content Content to get the formatted tags for
     * @return Given Content's tags formatted for display
     */
    public static String formatTagsForDisplay(@NonNull final Content content) {
        Set<Attribute> tagsAttributes = content.getAttributeMap().get(AttributeType.TAG);
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
    public static @DrawableRes int getFlagResourceId(@NonNull final Context context,
                                                     @NonNull final Content content) {
        Set<Attribute> langAttributes = content.getAttributeMap().get(AttributeType.LANGUAGE);
        if (langAttributes != null && !langAttributes.isEmpty())
            for (Attribute lang : langAttributes) {
                @DrawableRes int resId = LanguageHelper.INSTANCE.getFlagFromLanguage(context, lang.getName());
                if (resId != 0) return resId;
            }
        return 0;
    }

    /**
     * Get the drawable ID for the given rating
     *
     * @param rating Rating to get the resource ID for (0 to 5)
     * @return Resource ID representing the given rating
     */
    public static @DrawableRes int getRatingResourceId(int rating) {
        return switch (rating) {
            case 1 -> R.drawable.ic_star_1;
            case 2 -> R.drawable.ic_star_2;
            case 3 -> R.drawable.ic_star_3;
            case 4 -> R.drawable.ic_star_4;
            case 5 -> R.drawable.ic_star_5;
            default -> R.drawable.ic_star_empty;
        };
    }

    /**
     * Format the given Content's artists for display
     *
     * @param context Context to use
     * @param content Content to get the formatted artists for
     * @return Given Content's artists formatted for display
     */
    public static String formatArtistForDisplay(@NonNull final Context context,
                                                @NonNull final Content content) {
        List<Attribute> attributes = new ArrayList<>();

        Set<Attribute> artistAttributes = content.getAttributeMap().get(AttributeType.ARTIST);
        if (artistAttributes != null) attributes.addAll(artistAttributes);
        Set<Attribute> circleAttributes = content.getAttributeMap().get(AttributeType.CIRCLE);
        if (circleAttributes != null) attributes.addAll(circleAttributes);

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
     * Format the given Content's series for display on book cards
     *
     * @param context Context to use
     * @param content Content to format
     * @return "Series" caption ready to be displayed on a book card
     */
    public static String formatSeriesForDisplay(@NonNull final Context context,
                                                @NonNull final Content content) {
        Set<Attribute> seriesAttributes = content.getAttributeMap().get(AttributeType.SERIE);
        if (seriesAttributes == null || seriesAttributes.isEmpty()) {
            return "";
        } else {
            List<String> allSeries = new ArrayList<>();
            for (Attribute attribute : seriesAttributes) {
                allSeries.add(attribute.getName());
            }
            String series = android.text.TextUtils.join(", ", allSeries);
            return context.getString(R.string.work_series, series);
        }
    }

    /**
     * Transform the given online URL into a working GlideUrl using the given Content's cookies
     * (useful when viewing queue screen before any image has been downloaded)
     *
     * @param imageUrl URL of the online picture to transform
     * @param content  Content to use for cookies / referer
     * @return Working GlideUrl pointing to the given image URL, using the correct cookies / referer
     */
    @Nullable
    public static GlideUrl bindOnlineCover(@NonNull final String imageUrl,
                                           @Nullable final Content content) {
        if (WebkitPackageHelper.INSTANCE.getWebViewAvailable()) {
            String cookieStr = null;
            String referer = null;
            LazyHeaders.Builder builder = new LazyHeaders.Builder();

            // Quickly skip JSON deserialization if there are no cookies in downloadParams
            if (content != null) {
                String downloadParamsStr = content.getDownloadParams();
                if (downloadParamsStr != null && downloadParamsStr.contains(HEADER_COOKIE_KEY)) {
                    Map<String, String> downloadParams = ContentHelper.parseDownloadParams(downloadParamsStr);
                    cookieStr = downloadParams.get(HEADER_COOKIE_KEY);
                    referer = downloadParams.get(HEADER_REFERER_KEY);
                }
                if (null == cookieStr)
                    cookieStr = HttpHelperKt.getCookies(content.getGalleryUrl());
                if (null == referer) referer = content.getGalleryUrl();
                builder = builder.addHeader(HEADER_COOKIE_KEY, cookieStr).addHeader(HEADER_REFERER_KEY, referer).addHeader(HEADER_USER_AGENT, content.getSite().getUserAgent());
            }

            return new GlideUrl(imageUrl, builder.build()); // From URL
        }
        return null;
    }

    /**
     * Find the best match for the given Content inside the library and queue
     *
     * @param context     Context to use
     * @param content     Content to find the duplicate for
     * @param useTitle    Use title as a duplicate criteria
     * @param useArtist   Use artist as a duplicate criteria
     * @param useLanguage Use language as a duplicate criteria
     * @param useCover    Use cover picture perceptual hash as a duplicate criteria
     * @param sensitivity Sensitivity to use
     * @param pHash       Cover picture perceptual hash to use as an override for the given Content's cover hash; Long.MIN_VALUE not to override
     * @param dao         DAO to use
     * @return Pair containing
     * - left side : Best match for the given Content inside the library and queue
     * - Right side : Similarity score (between 0 and 1; 1=100%)
     */
    @Nullable
    public static Pair<Content, Float> findDuplicate(@NonNull final Context context,
                                                     @NonNull final Content content, boolean useTitle, boolean useArtist, boolean useLanguage,
                                                     boolean useCover, int sensitivity, long pHash, @NonNull final CollectionDAO dao) {
        // First find good rough candidates by searching for the longest word in the title
        String[] words = StringHelper.cleanMultipleSpaces(StringHelper.simplify(content.getTitle())).split(" ");
        Optional<String> longestWord = Stream.of(words).sorted(Comparator.comparingInt(String::length)).findLast();
        // Too many resources consumed if the longest word is 1 character long
        if (longestWord.isEmpty() || longestWord.get().length() < 2) return null;

        int[] contentStatuses = ArrayUtils.addAll(libraryStatus, queueTabStatus);
        List<Content> roughCandidates = dao.searchTitlesWith(longestWord.get(), contentStatuses);
        if (roughCandidates.isEmpty()) return null;

        if (!useCover) pHash = Long.MIN_VALUE;

        // Compute cover hashes for selected candidates
        for (Content c : roughCandidates)
            if (0 == c.getCover().getImageHash()) computeAndSaveCoverHash(context, c, dao);

        // Refine by running the actual duplicate detection algorithm against the rough candidates
        List<DuplicateEntry> entries = new ArrayList<>();
        StringSimilarity cosine = new Cosine();
        DuplicateCandidate reference = new DuplicateCandidate(content, useTitle, useArtist, useLanguage, useCover, true, pHash);
        List<DuplicateCandidate> candidates = Stream.of(roughCandidates).map(c -> new DuplicateCandidate(c, useTitle, useArtist, useLanguage, useCover, true, Long.MIN_VALUE)).toList();
        for (DuplicateCandidate candidate : candidates) {
            DuplicateEntry entry = DuplicateHelperKt.processContent(reference, candidate, useTitle, useCover, useArtist, useLanguage, true, sensitivity, cosine);
            if (entry != null) entries.add(entry);
        }
        // Sort by similarity and size (unfortunately, Comparator.comparing is API24...)
        Optional<DuplicateEntry> bestMatch = Stream.of(entries).sorted(DuplicateEntry::compareTo).findFirst();
        if (bestMatch.isPresent()) {
            Content resultContent = dao.selectContent(bestMatch.get().getDuplicateId());
            float resultScore = bestMatch.get().calcTotalScore();
            return new Pair<>(resultContent, resultScore);
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
    public static void computeAndSaveCoverHash(@NonNull final Context context,
                                               @NonNull final Content content, @NonNull final CollectionDAO dao) {
        Bitmap coverBitmap = DuplicateHelperKt.getCoverBitmapFromContent(context, content);
        long pHash = DuplicateHelperKt.calcPhash(DuplicateHelperKt.getHashEngine(), coverBitmap);
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
        if (null == images || images.isEmpty()) return false;

        // Pick a random picture
        ImageFile img = images.get(Helper.getRandomInt(images.size()));

        // Peek it to see if downloads work
        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HEADER_REFERER_KEY, content.getReaderUrl())); // Useful for Hitomi and Toonily

        try {
            if (img.needsPageParsing()) {
                // Get cookies from the app jar
                String cookieStr = HttpHelperKt.getCookies(img.getPageUrl());
                // If nothing found, peek from the site
                if (cookieStr.isEmpty())
                    cookieStr = HttpHelperKt.peekCookies(img.getPageUrl());
                if (!cookieStr.isEmpty())
                    headers.add(new Pair<>(HEADER_COOKIE_KEY, cookieStr));
                return testDownloadPictureFromPage(content.getSite(), img, headers);
            } else {
                // Get cookies from the app jar
                String cookieStr = HttpHelperKt.getCookies(img.getUrl());
                // If nothing found, peek from the site
                if (cookieStr.isEmpty())
                    cookieStr = HttpHelperKt.peekCookies(content.getGalleryUrl());
                if (!cookieStr.isEmpty())
                    headers.add(new Pair<>(HEADER_COOKIE_KEY, cookieStr));
                return testDownloadPicture(content.getSite(), img, headers);
            }
        } catch (IOException | LimitReachedException | EmptyResultException |
                 CloudflareHelper.CloudflareProtectedException e) {
            Timber.w(e);
        }
        return false;
    }

    /**
     * Test if online pages for the given Chapter are downloadable
     * NB : Implementation does not test all pages but one page picked randomly
     *
     * @param chapter Chapter whose pages to test
     * @return True if pages are downloadable; false if they aren't
     */
    public static boolean isDownloadable(@NonNull final Chapter chapter) {
        List<ImageFile> images = chapter.getImageList();
        if (null == images || images.isEmpty()) return false;

        Content content = DBHelper.reach(chapter, chapter.getContent());
        if (null == content) return false;

        // Pick a random picture
        ImageFile img = images.get(Helper.getRandomInt(images.size()));

        // Peek it to see if downloads work
        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HEADER_REFERER_KEY, content.getReaderUrl())); // Useful for Hitomi and Toonily

        try {
            if (img.needsPageParsing()) {
                // Get cookies from the app jar
                String cookieStr = HttpHelperKt.getCookies(img.getPageUrl());
                // If nothing found, peek from the site
                if (cookieStr.isEmpty())
                    cookieStr = HttpHelperKt.peekCookies(img.getPageUrl());
                if (!cookieStr.isEmpty())
                    headers.add(new Pair<>(HEADER_COOKIE_KEY, cookieStr));
                return testDownloadPictureFromPage(content.getSite(), img, headers);
            } else {
                // Get cookies from the app jar
                String cookieStr = HttpHelperKt.getCookies(img.getUrl());
                // If nothing found, peek from the site
                if (cookieStr.isEmpty())
                    cookieStr = HttpHelperKt.peekCookies(chapter.getUrl());
                if (!cookieStr.isEmpty())
                    headers.add(new Pair<>(HEADER_COOKIE_KEY, cookieStr));
                return testDownloadPicture(content.getSite(), img, headers);
            }
        } catch (IOException | LimitReachedException | EmptyResultException |
                 CloudflareHelper.CloudflareProtectedException e) {
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
    private static boolean testDownloadPictureFromPage(
            @NonNull Site site,
            @NonNull ImageFile img,
            List<Pair<String, String>> requestHeaders) throws
            IOException, LimitReachedException, EmptyResultException, CloudflareHelper.CloudflareProtectedException {
        String pageUrl = HttpHelperKt.fixUrl(img.getPageUrl(), site.getUrl());
        ImageListParser parser = ContentParserFactory.INSTANCE.getImageListParser(site);
        Pair<String, String> pages = parser.parseImagePage(pageUrl, requestHeaders);
        img.setUrl(pages.getFirst());
        // Download the picture
        try {
            return testDownloadPicture(site, img, requestHeaders);
        } catch (IOException | CloudflareHelper.CloudflareProtectedException e) {
            if (pages.getSecond() != null) Timber.d("First download failed; trying backup URL");
            else throw e;
        }
        // Trying with backup URL
        img.setUrl(pages.getSecond());
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
    private static boolean testDownloadPicture(
            @NonNull Site site,
            @NonNull ImageFile img,
            List<Pair<String, String>> requestHeaders) throws IOException, CloudflareHelper.CloudflareProtectedException {
        String url = HttpHelperKt.fixUrl(img.getUrl(), site.getUrl());

        Pair<ResponseBody, String> response = fetchBodyFast(url, site, requestHeaders, null);
        ResponseBody body = response.getFirst();
        if (null == body)
            throw new IOException("Could not read response : empty body for " + img.getUrl());

        byte[] buffer = new byte[50];
        // Read mime-type on the fly
        try (InputStream in = body.byteStream()) {
            if (in.read(buffer) > -1) {
                String mimeType = ImageHelperKt.getMimeTypeFromPictureBinary(buffer);
                Timber.d("Testing online picture accessibility : found %s at %s", mimeType, img.getUrl());
                return (!mimeType.isEmpty() && !mimeType.equals(MIME_IMAGE_GENERIC));
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
            boolean appendBookTitle,
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
        mergedContent.setDownloadCompletionDate(Instant.now().toEpochMilli());
        mergedContent.setStatus(firstContent.getStatus());
        mergedContent.setFavourite(firstContent.isFavourite());
        mergedContent.setRating(firstContent.getRating());
        mergedContent.setBookPreferences(firstContent.getBookPreferences());
        mergedContent.setManuallyMerged(true);

        // Merge attributes
        List<Attribute> mergedAttributes = Stream.of(contentList).flatMap(c -> Stream.of(c.getAttributes())).toList();
        mergedContent.addAttributes(mergedAttributes);

        // Create destination folder for new content
        DocumentFile targetFolder;
        // External library root for external content
        if (mergedContent.getStatus().equals(StatusContent.EXTERNAL)) {
            DocumentFile externalRootFolder = getDocumentFromTreeUriString(context, Preferences.getExternalLibraryUri());
            if (null == externalRootFolder || !externalRootFolder.exists())
                throw new ContentNotProcessedException(mergedContent, "Could not create target directory : external root unreachable");

            Pair<String, String> bookFolderName = formatBookFolderName(mergedContent);
            // First try finding the folder with new naming...
            targetFolder = findFolder(context, externalRootFolder, bookFolderName.getFirst());
            if (null == targetFolder) { // ...then with old (sanitized) naming...
                targetFolder = findFolder(context, externalRootFolder, bookFolderName.getSecond());
                if (null == targetFolder) { // ...if not, create a new folder with the new naming...
                    targetFolder = externalRootFolder.createDirectory(bookFolderName.getFirst());
                    if (null == targetFolder) { // ...if it fails, create a new folder with the old naming
                        targetFolder = externalRootFolder.createDirectory(bookFolderName.getSecond());
                    }
                }
            }
        } else { // Primary folder for non-external content; using download strategy
            StorageLocation location = DownloadHelperKt.selectDownloadLocation(context);
            targetFolder = ContentHelper.getOrCreateContentDownloadDir(context, mergedContent, location, true);
        }
        if (null == targetFolder || !targetFolder.exists())
            throw new ContentNotProcessedException(mergedContent, "Could not create target directory");

        mergedContent.setStorageDoc(targetFolder);

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
                String extension = HttpHelperKt.getExtensionFromUri(firstCover.getFileUri());
                Uri newUri = copyFile(context, Uri.parse(firstCover.getFileUri()), targetFolder.getUri(), firstCover.getMimeType(), firstCover.getName() + "." + extension);
                if (newUri != null) coverPic.setFileUri(newUri.toString());
                else Timber.w("Could not move file %s", firstCover.getFileUri());
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
                    newImg.setFileUri(""); // Clear initial URI
                    newImg.setOrder(pictureOrder++);
                    newImg.computeName(nbMaxDigits);
                    Chapter chapLink = img.getLinkedChapter();
                    if (null == chapLink) { // No chapter -> set content chapter
                        newChapter = contentChapter;
                    } else {
                        if (chapLink.getUniqueId().isEmpty()) chapLink.populateUniqueId();
                        if (null == newChapter || !chapLink.getUniqueId().equals(newChapter.getUniqueId())) {
                            newChapter = Chapter.fromChapter(chapLink).setOrder(chapterOrder++);
                            if (appendBookTitle)
                                newChapter.setName(c.getTitle() + " - " + newChapter.getName());
                        }
                    }
                    if (!mergedChapters.contains(newChapter)) mergedChapters.add(newChapter);
                    newImg.setChapter(newChapter);

                    // If exists, move the picture to the merged books' folder
                    if (isInLibrary(newImg.getStatus())) {
                        String extension = HttpHelperKt.getExtensionFromUri(img.getFileUri());
                        Uri newUri = copyFile(context, Uri.parse(img.getFileUri()), targetFolder.getUri(), newImg.getMimeType(), newImg.getName() + "." + extension);
                        if (newUri != null) newImg.setFileUri(newUri.toString());
                        else Timber.w("Could not move file %s", img.getFileUri());
                        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.Type.PROGRESS, R.id.generic_progress, 0, nbProcessedPics++, 0, (int) nbImages));
                    }
                    mergedImages.add(newImg);
                }
            }
        } catch (IOException e) {
            Timber.w(e);
            isError = true;
        }

        if (!isError) {
            mergedContent.setImageFiles(mergedImages);
            mergedContent.setChapters(mergedChapters); // Chapters have to be attached to Content too
            mergedContent.setQtyPages(mergedImages.size() - 1);
            mergedContent.computeSize();

            DocumentFile jsonFile = ContentHelper.createJson(context, mergedContent);
            if (jsonFile != null) mergedContent.setJsonUri(jsonFile.getUri().toString());

            // Save new content (incl. non-custom group operations)
            ContentHelper.addContent(context, dao, mergedContent);

            // Merge custom groups and update
            // Merged book can be a member of one custom group only
            Optional<Group> customGroup = Stream.of(contentList).flatMap(c -> Stream.of(c.groupItems)).map(GroupItem::getGroup).withoutNulls().distinct().filter(g -> g.grouping.equals(Grouping.CUSTOM)).findFirst();
            if (customGroup.isPresent())
                GroupHelper.moveContentToCustomGroup(mergedContent, customGroup.get(), dao);
        }

        EventBus.getDefault().postSticky(new ProcessEvent(ProcessEvent.Type.COMPLETE, R.id.generic_progress, 0, (int) nbImages, 0, (int) nbImages));
    }

    public static StorageLocation getLocation(Content content) {
        for (StorageLocation location : StorageLocation.getEntries()) {
            String rootUri = Preferences.getStorageUri(location);
            if (!rootUri.isEmpty() && content.getStorageUri().startsWith(rootUri))
                return location;
        }
        return StorageLocation.NONE;
    }

    public static void purgeContent(@NonNull final Context context,
                                    @NonNull final Content content, boolean keepCover, boolean isDownloadPrepurge) {
        DeleteData.Builder builder = new DeleteData.Builder();
        builder.setContentPurgeIds(Stream.of(content).map(Content::getId).toList());
        builder.setContentPurgeKeepCovers(keepCover);
        builder.setDownloadPrepurge(isDownloadPrepurge);

        WorkManager workManager = WorkManager.getInstance(context);
        workManager.enqueueUniqueWork(Integer.toString(R.id.delete_service_purge), ExistingWorkPolicy.APPEND_OR_REPLACE, new OneTimeWorkRequest.Builder(PurgeWorker.class).setInputData(builder.getData()).build());
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
    private static class InnerNameNumberArchiveComparator implements Comparator<ArchiveEntry> {
        @Override
        public int compare(@NonNull ArchiveEntry o1, @NonNull ArchiveEntry o2) {
            return CaseInsensitiveSimpleNaturalComparator.getInstance().compare(o1.getPath(), o2.getPath());
        }
    }

    /**
     * Comparator to be used to sort Contents according to their titles
     */
    public static class InnerNameNumberContentComparator implements Comparator<Content> {
        @Override
        public int compare(@NonNull Content c1, @NonNull Content c2) {
            return CaseInsensitiveSimpleNaturalComparator.getInstance().compare(c1.getTitle(), c2.getTitle());
        }
    }
}
