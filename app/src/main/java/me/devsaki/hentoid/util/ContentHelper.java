package me.devsaki.hentoid.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;

import org.threeten.bp.Instant;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Arrays;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImageViewerActivity;
import me.devsaki.hentoid.activities.UnlockActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.JsonContent;
import timber.log.Timber;

import static me.devsaki.hentoid.util.FileHelper.getDefaultDir;
import static me.devsaki.hentoid.util.FileHelper.getExtSdCardFolder;
import static me.devsaki.hentoid.util.FileHelper.isSAF;
import static me.devsaki.hentoid.util.FileUtil.deleteQuietly;

/**
 * Utility class for Content-related operations
 */
public final class ContentHelper {

    private static final String UNAUTHORIZED_CHARS = "[^a-zA-Z0-9.-]";


    private ContentHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static void viewContent(@NonNull final Context context, @NonNull Content content) {
        viewContent(context, content, false);
    }

    public static void viewContent(@NonNull final Context context, @NonNull Content content, boolean wrapPin) {
        Intent intent = new Intent(context, content.getWebActivityClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        BaseWebActivityBundle.Builder builder = new BaseWebActivityBundle.Builder();
        builder.setUrl(content.getGalleryUrl());
        intent.putExtras(builder.getBundle());
        if (wrapPin) intent = UnlockActivity.wrapIntent(context, intent);
        context.startActivity(intent);
    }

    public static void updateJson(@Nonnull Context context, @Nonnull Content content) {
        DocumentFile file = DocumentFile.fromSingleUri(context, Uri.parse(content.getJsonUri()));
        if (null == file)
            throw new InvalidParameterException("'" + content.getJsonUri() + "' does not refer to a valid file");

        try {
            JsonHelper.updateJson(JsonContent.fromEntity(content), JsonContent.class, file);
        } catch (IOException e) {
            Timber.e(e, "Error while writing to %s", content.getJsonUri());
        }
    }

    public static void createJson(@Nonnull Content content) {
        File dir = getContentDownloadDir(content);
        try {
            JsonHelper.createJson(JsonContent.fromEntity(content), JsonContent.class, dir);
        } catch (IOException e) {
            Timber.e(e, "Error while writing to %s", dir.getAbsolutePath());
        }
    }

    /**
     * Open the given file using the device's app(s) of choice
     *
     * @param context Context
     * @param aFile   File to be opened
     */
    public static void openFile(Context context, File aFile) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        File file = new File(aFile.getAbsolutePath());
        String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        myIntent.setDataAndType(Uri.fromFile(file), mimeType);
        try {
            context.startActivity(myIntent);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "Activity not found to open %s", aFile.getAbsolutePath());
            ToastUtil.toast(context, R.string.error_open, Toast.LENGTH_LONG);
        }
    }

    /**
     * Open built-in image viewer telling it to display the images of the given Content
     *
     * @param context Context
     * @param content Content to be displayed
     */
    public static void openHentoidViewer(@NonNull Context context, @NonNull Content content, Bundle searchParams) {
        ImageViewerActivityBundle.Builder builder = new ImageViewerActivityBundle.Builder();
        builder.setContentId(content.getId());
        if (searchParams != null) builder.setSearchParams(searchParams);

        Intent viewer = new Intent(context, ImageViewerActivity.class);
        viewer.putExtras(builder.getBundle());

        context.startActivity(viewer);
    }

    @WorkerThread
    public static Content open(@Nonnull Context context, @NonNull Content content, Bundle searchParams) {


        String rootFolderName = Preferences.getRootFolderName();
        File dir = new File(rootFolderName, content.getStorageFolder());

        Timber.d("Opening: %s from: %s", content.getTitle(), dir);
        if (isSAF() && getExtSdCardFolder(new File(rootFolderName)) == null) {
            Timber.d("File not found!! Exiting method.");
            ToastUtil.toast(R.string.sd_access_error);
            return content;
        }

        File imageFile = null;
        File[] files = dir.listFiles(
                file -> (file.isFile() && !file.getName().toLowerCase().startsWith("thumb") &&
                        (
                                file.getName().toLowerCase().endsWith("jpg")
                                        || file.getName().toLowerCase().endsWith("jpeg")
                                        || file.getName().toLowerCase().endsWith("png")
                                        || file.getName().toLowerCase().endsWith("gif")
                                        || file.getName().toLowerCase().endsWith("webp")
                        )
                )
        );

        if (files != null && files.length > 0) {
            Arrays.sort(files);
            imageFile = files[0];
        }
        if (imageFile == null) {
            String message = context.getString(R.string.image_file_not_found)
                    .replace("@dir", dir.getAbsolutePath());
            ToastUtil.toast(context, message);
        } else {
            int readContentPreference = Preferences.getContentReadAction();
            if (readContentPreference == Preferences.Constant.PREF_READ_CONTENT_PHONE_DEFAULT_VIEWER) {
                openFile(context, imageFile);
            }  else if (readContentPreference == Preferences.Constant.PREF_READ_CONTENT_HENTOID_VIEWER) {
                openHentoidViewer(context, content, searchParams);
            }
        }
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        content.increaseReads().setLastReadDate(Instant.now().toEpochMilli());
        db.insertContent(content);

        if (!content.getJsonUri().isEmpty()) updateJson(context, content);
        else createJson(content);

        return content;
    }


    /**
     * Method is used by onBindViewHolder(), speed is key
     */
    public static String getThumb(Content content) {
        String coverUrl = content.getCoverImageUrl();

        // If trying to access a non-downloaded book cover (e.g. viewing the download queue)
        if (content.getStorageFolder().equals("")) return coverUrl;

        String extension = HttpHelper.getExtensionFromUri(coverUrl);
        // Some URLs do not link the image itself (e.g Tsumino) => jpg by default
        // NB : ideal would be to get the content-type of the resource behind coverUrl, but that's too time-consuming
        if (extension.isEmpty() || extension.contains("/")) extension = "jpg";

        File f = new File(Preferences.getRootFolderName(), content.getStorageFolder() + File.separator + "thumb." + extension);
        return f.exists() ? f.getAbsolutePath() : coverUrl;
    }

    @Nullable
    public static File[] getPictureFilesFromContent(Content content) {
        String rootFolderName = Preferences.getRootFolderName();
        File dir = new File(rootFolderName, content.getStorageFolder());

        if (isSAF() && getExtSdCardFolder(new File(rootFolderName)) == null) {
            Timber.d("File not found!! Exiting method.");
            ToastUtil.toast(R.string.sd_access_error);
            return null;
        }

        return dir.listFiles(
                file -> (file.isFile()
                        && !file.getName().toLowerCase().startsWith("thumb")
                        && Helper.isImageExtensionSupported(FileHelper.getExtension(file.getName()))
                )
        );
    }


    @WorkerThread
    public static void removeContent(@NonNull Content content) {
        // If the book has just starting being downloaded and there are no complete pictures on memory yet, it has no storage folder => nothing to delete
        if (!content.getStorageFolder().isEmpty()) {
            File dir = getContentDownloadDir(content);
            if (deleteQuietly(dir) || FileUtil.deleteWithSAF(dir)) {
                Timber.i("Directory %s removed.", dir);
            } else {
                Timber.w("Failed to delete directory: %s", dir);
            }
        }
    }

    /**
     * Create the download directory of the given content
     *
     * @param context Context
     * @param content Content for which the directory to create
     * @return Created directory
     */
    public static File createContentDownloadDir(Context context, Content content) {
        String folderDir = formatDirPath(content);

        String settingDir = Preferences.getRootFolderName();
        if (settingDir.isEmpty()) {
            settingDir = getDefaultDir(context, folderDir).getAbsolutePath();
        }

        Timber.d("New book directory %s in %s", folderDir, settingDir);

        File file = new File(settingDir, folderDir);
        if (!file.exists() && !FileUtil.makeDir(file)) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                FileUtil.makeDir(file);
            }
        }

        return file;
    }

    public static File getContentDownloadDir(Content content) {
        String rootFolderName = Preferences.getRootFolderName();
        return new File(rootFolderName, content.getStorageFolder());
    }

    /**
     * Format the download directory path of the given content according to current user preferences
     *
     * @param content Content to get the path from
     * @return Canonical download directory path of the given content, according to current user preferences
     */
    public static String formatDirPath(Content content) {
        String siteFolder = content.getSite().getFolder();
        String result = siteFolder;

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
        int titleLength = result.length() - siteFolder.length();
        if ((truncLength > 0) && ((titleLength + suffix.length()) > truncLength))
            result = result.substring(0, siteFolder.length() + truncLength - suffix.length() - 1);

        result += suffix;

        return result;
    }

    @SuppressWarnings("squid:S2676") // Math.abs is used for formatting purposes only
    private static String formatBookId(Content content) {
        String id = content.getUniqueSiteId();
        // For certain sources (8muses), unique IDs are strings that may be very long
        // => shorten them by using their hashCode
        if (id.length() > 10) id = Helper.formatIntAsStr(Math.abs(id.hashCode()), 10);
        return "[" + id + "]";
    }

    public static File getOrCreateSiteDownloadDir(Context context, Site site) {
        File file;
        String settingDir = Preferences.getRootFolderName();
        String folderDir = site.getFolder();
        if (settingDir.isEmpty()) {
            return getDefaultDir(context, folderDir);
        }
        file = new File(settingDir, folderDir);
        if (!file.exists() && !FileUtil.makeDir(file)) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                FileUtil.makeDir(file);
            }
        }

        return file;
    }

    public static void shareContent(final Context context, final Content item) {
        String url = item.getGalleryUrl();

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, item.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, url);

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_to)));
    }
}
