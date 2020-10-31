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
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.threeten.bp.Instant;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImageViewerActivity;
import me.devsaki.hentoid.activities.UnlockActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.util.exception.FileNotRemovedException;
import timber.log.Timber;

import static com.annimon.stream.Collectors.toList;

/**
 * Utility class for Content-related operations
 */
public final class ContentHelper {

    private static final String UNAUTHORIZED_CHARS = "[^a-zA-Z0-9.-]";
    private static final int[] libraryStatus = new int[]{StatusContent.DOWNLOADED.getCode(), StatusContent.MIGRATED.getCode(), StatusContent.EXTERNAL.getCode()};

    // TODO empty this cache at some point
    private static final Map<String, String> fileNameMatchCache = new HashMap<>();


    private ContentHelper() {
        throw new IllegalStateException("Utility class");
    }


    public static int[] getLibraryStatuses() {
        return libraryStatus;
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
        if (content.isArchive()) return;

        DocumentFile file = FileHelper.getFileFromSingleUriString(context, content.getJsonUri());
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
        if (content.isArchive()) return;

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
        // Save current queue (to be able to restore it in case the app gets uninstalled)
        List<Content> queuedContent = Stream.of(queue).map(qr -> qr.content.getTarget()).withoutNulls().toList();
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
            for (File f : images) FileHelper.removeFile(f);
        } else { // Remove a folder and its content
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

    // TODO doc
    public static void removeAllExternalContent(@NonNull final Context context) {
        // Remove all external books from DB
        CollectionDAO dao = new ObjectBoxDAO(context);
        try {
            dao.deleteAllExternalBooks();
        } finally {
            dao.cleanup();
        }

        // Remove all images stored in the app's persistent folder (archive covers)
        File appFolder = context.getFilesDir();
        File[] images = appFolder.listFiles((dir, name) -> ImageHelper.isSupportedImage(name));
        for (File f : images) FileHelper.removeFile(f);
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
        List<QueueRecord> queue = dao.selectQueue();
        if (!queue.isEmpty() && queue.get(0).content.getTargetId() == content.getId())
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_CANCEL));

        // Remove from queue
        dao.deleteQueue(content);

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

                        for (Attribute a : artists) {
                            Group group = a.getGroup().getTarget();
                            if (null == group) {
                                group = new Group(Grouping.ARTIST, a.getName(), ++nbGroups);
                                if (!a.contents.isEmpty())
                                    group.picture.setTarget(a.contents.get(0).getCover());
                            }
                            GroupHelper.addContentToAttributeGroup(dao, group, a, content);
                        }
                    }
                }
        }

        // Extract the cover to the app's persistent folder if the book is an archive
        if (content.isArchive()) {
            DocumentFile archive = FileHelper.getFileFromSingleUriString(context, content.getStorageUri());
            try {
                List<Uri> outputFiles = ArchiveHelper.extractZipEntries(
                        context,
                        archive,
                        Stream.of(content.getCover().getFileUri().replace(content.getStorageUri() + File.separator, "")).toList(),
                        context.getFilesDir(),
                        Stream.of(newContentId + "").toList());
                if (!outputFiles.isEmpty()) {
                    content.getCover().setFileUri(outputFiles.get(0).toString());
                    dao.replaceImageList(newContentId, content.getImageFiles());
                }
            } catch (IOException e) {
                Timber.w(e);
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
        List<Long> contents = Stream.of(images).filter(i -> i.content != null).map(i -> i.content.getTargetId()).distinct().toList();

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
        Content content = dao.selectContent(newCover.content.getTargetId());
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

        String title = content.getTitle();
        title = (null == title) ? "" : title.replaceAll(UNAUTHORIZED_CHARS, "_");
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
        // Sort files by anything that resembles a number inside their names
        List<DocumentFile> fileList = Stream.of(files).withoutNulls().sorted(new InnerNameNumberFileComparator()).collect(toList());
        for (DocumentFile f : fileList) {
            String name = namePrefix + ((f.getName() != null) ? f.getName() : "");
            ImageFile img = new ImageFile();
            if (name.startsWith(Consts.THUMB_FILE_NAME)) img.setIsCover(true);
            else order++;
            img.setName(FileHelper.getFileNameWithoutExtension(name)).setOrder(order).setUrl(f.getUri().toString()).setStatus(targetStatus).setFileUri(f.getUri().toString()).setSize(f.length());
            img.setMimeType(FileHelper.getMimeTypeFromFileName(name));
            result.add(img);
        }
        return result;
    }

    public static List<ImageFile> createImageListFromZipEntries(
            @NonNull final Uri zipFileUri,
            @NonNull final List<ZipEntry> files,
            @NonNull final StatusContent targetStatus,
            int startingOrder,
            @NonNull final String namePrefix) {
        Helper.assertNonUiThread();
        List<ImageFile> result = new ArrayList<>();
        int order = startingOrder;
        // Sort files by anything that resembles a number inside their names (default entry order from ZipInputStream is chaotic)
        List<ZipEntry> fileList = Stream.of(files).withoutNulls().sorted(new InnerNameNumberZipComparator()).collect(toList());
        for (ZipEntry f : fileList) {
            String name = namePrefix + f.getName();
            String path = zipFileUri.toString() + File.separator + f.getName();
            ImageFile img = new ImageFile();
            if (name.startsWith(Consts.THUMB_FILE_NAME)) img.setIsCover(true);
            else order++;
            img.setName(FileHelper.getFileNameWithoutExtension(name)).setOrder(order).setUrl(path).setStatus(targetStatus).setFileUri(path).setSize(f.getSize());
            img.setMimeType(FileHelper.getMimeTypeFromFileName(name));
            result.add(img);
        }
        return result;
    }

    /**
     * Comparator to be used to sort files according to their names :
     * - Sort according to the concatenation of all its numerical characters, if any
     * - If none, sort alphabetically (default string compare)
     */
    private static class InnerNameNumberFileComparator implements Comparator<DocumentFile> {
        @Override
        public int compare(@NonNull DocumentFile o1, @NonNull DocumentFile o2) {
            String name1 = o1.getName();
            if (null == name1) name1 = "";
            String name2 = o2.getName();
            if (null == name2) name2 = "";

            return new InnerNameNumberComparator().compare(name1, name2);
        }
    }

    private static class InnerNameNumberZipComparator implements Comparator<ZipEntry> {
        @Override
        public int compare(@NonNull ZipEntry o1, @NonNull ZipEntry o2) {
            return new ContentHelper.InnerNameNumberComparator().compare(o1.getName(), o2.getName());
        }
    }

    public static class InnerNameNumberComparator implements Comparator<String> {
        @Override
        public int compare(@NonNull String name1, @NonNull String name2) {
            long innerNumber1 = Helper.extractNumeric(name1);
            if (-1 == innerNumber1) return name1.compareTo(name2);
            long innerNumber2 = Helper.extractNumeric(name2);
            if (-1 == innerNumber2) return name1.compareTo(name2);

            return Long.compare(innerNumber1, innerNumber2);
        }
    }
}
