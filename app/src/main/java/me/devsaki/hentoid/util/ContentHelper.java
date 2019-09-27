package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImageViewerActivity;
import me.devsaki.hentoid.activities.UnlockActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import timber.log.Timber;

import static me.devsaki.hentoid.util.FileHelper.deleteQuietly;
import static me.devsaki.hentoid.util.FileHelper.getDefaultDir;
import static me.devsaki.hentoid.util.FileHelper.getExtSdCardFolder;
import static me.devsaki.hentoid.util.FileHelper.getExtension;
import static me.devsaki.hentoid.util.FileHelper.isSAF;

/**
 * Utility class for Content-related operations
 */
public final class ContentHelper {

    private static final String AUTHORIZED_CHARS = "[^a-zA-Z0-9.-]";


    private ContentHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static void viewContent(final Context context, Content content) {
        viewContent(context, content, false);
    }

    public static void viewContent(final Context context, Content content, boolean wrapPin) {
        Intent intent = new Intent(context, content.getWebActivityClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        BaseWebActivityBundle.Builder builder = new BaseWebActivityBundle.Builder();
        builder.setUrl(content.getGalleryUrl());
        intent.putExtras(builder.getBundle());
        if (wrapPin) intent = UnlockActivity.wrapIntent(context, intent);
        context.startActivity(intent);
    }


    public static List<Long> extractAttributeIdsByType(List<Attribute> attrs, AttributeType type) {
        return extractAttributeIdsByType(attrs, new AttributeType[]{type});
    }

    private static List<Long> extractAttributeIdsByType(List<Attribute> attrs, AttributeType[] types) {
        List<Long> result = new ArrayList<>();

        for (Attribute a : attrs) {
            for (AttributeType type : types) {
                if (a.getType().equals(type)) result.add(a.getId());
            }
        }

        return result;
    }


    public static void updateJson(@Nonnull Context context, @Nonnull Content content) {
        DocumentFile file = DocumentFile.fromSingleUri(context, Uri.parse(content.getJsonUri()));
        if (null == file)
            throw new InvalidParameterException("'" + content.getJsonUri() + "' does not refer to a valid file");

        try {
            JsonHelper.updateJson(content.preJSONExport(), file);
        } catch (IOException e) {
            Timber.e(e, "Error while writing to %s", content.getJsonUri());
        }
    }

    public static void createJson(@Nonnull Content content) {
        File dir = getContentDownloadDir(content);
        try {
            JsonHelper.createJson(content.preJSONExport(), dir);
        } catch (IOException e) {
            Timber.e(e, "Error while writing to %s", dir.getAbsolutePath());
        }
    }


    public static void archiveContent(final Context context, Content content) {
        Timber.d("Building file list for: %s", content.getTitle());
        // Build list of files

        File dir = getContentDownloadDir(content);

        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
            Arrays.sort(files);
            ArrayList<File> fileList = new ArrayList<>();
            for (File file : files) {
                String filename = file.getName();
                if (filename.endsWith(".json") || filename.contains("thumb")) {
                    break;
                }
                fileList.add(file);
            }

            // Create folder to share from
            File sharedDir = new File(context.getExternalCacheDir() + "/shared");
            if (FileUtil.makeDir(sharedDir)) {
                Timber.d("Shared folder created.");
            }

            // Clean directory (in case of previous job)
            if (FileHelper.cleanDirectory(sharedDir)) {
                Timber.d("Shared folder cleaned up.");
            }

            // Build destination file
            File dest = new File(context.getExternalCacheDir() + "/shared",
                    content.getTitle().replaceAll(AUTHORIZED_CHARS, "_") + ".zip");
            Timber.d("Destination file: %s", dest);

            // Convert ArrayList to Array
            File[] fileArray = fileList.toArray(new File[0]);
            // Compress files
            new FileHelper.AsyncUnzip(context, dest).execute(fileArray, dest);
        }
    }


    /**
     * Open built-in image viewer telling it to display the images of the given Content
     *
     * @param context Context
     * @param content Content to be displayed
     */
    private static void openHentoidViewer(@NonNull Context context, @NonNull Content content, Bundle searchParams) {
        ImageViewerActivityBundle.Builder builder = new ImageViewerActivityBundle.Builder();
        builder.setContentId(content.getId());
        if (searchParams != null) builder.setSearchParams(searchParams);

        Intent viewer = new Intent(context, ImageViewerActivity.class);
        viewer.putExtras(builder.getBundle());

        context.startActivity(viewer);
    }


    /**
     * Open the given content using the viewer defined in user preferences
     *
     * @param context Context
     * @param content Content to be opened
     */
    public static void openContent(final Context context, Content content) {
        openContent(context, content, null);
    }

    public static void openContent(final Context context, Content content, Bundle searchParams) {
        Timber.d("Opening: %s from: %s", content.getTitle(), content.getStorageFolder());
        ToastUtil.toast("Opening: " + content.getTitle());

        openHentoidViewer(context, content, searchParams);
    }

    @Nullable
    public static Content updateContentReads(@Nonnull Context context, long contentId) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        Content content = db.selectContentById(contentId);
        if (content != null) {
            content.increaseReads().setLastReadDate(new Date().getTime());
            db.updateContentReads(content);

            if (!content.getJsonUri().isEmpty()) updateJson(context, content);
            else createJson(content);

            return content;
        }
        return null;
    }


    /**
     * Method is used by onBindViewHolder(), speed is key
     */
    public static String getThumb(Content content) {
        String coverUrl = content.getCoverImageUrl();

        // If trying to access a non-downloaded book cover (e.g. viewing the download queue)
        if (content.getStorageFolder().equals("")) return coverUrl;

        String extension = getExtension(coverUrl);
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

        Timber.d("Opening: %s from: %s", content.getTitle(), dir);
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
    public static void removeContent(Content content) {
        // If the book has just starting being downloaded and there are no complete pictures on memory yet, it has no storage folder => nothing to delete
        if (content.getStorageFolder().length() > 0) {
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
        int folderNamingPreference = Preferences.getFolderNameFormat();

        if (folderNamingPreference == Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID) {
            result += content.getAuthor().replaceAll(AUTHORIZED_CHARS, "_") + " - ";
        }
        if (folderNamingPreference == Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID || folderNamingPreference == Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_TITLE_ID) {
            result += content.getTitle().replaceAll(AUTHORIZED_CHARS, "_") + " - ";
        }

        // Unique content ID
        String suffix = "[" + formatBookId(content) + "]";

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
        // For certain sources (8muses, fakku), unique IDs are strings that may be very long
        // => shorten them by using their hashCode
        if (id.length() > 10) id = Helper.formatIntAsStr(Math.abs(id.hashCode()), 10);
        return id;
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
}
