package me.devsaki.hentoid.util.download;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.function.Consumer;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StorageLocation;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.exception.DownloadInterruptedException;
import me.devsaki.hentoid.util.exception.NetworkingException;
import me.devsaki.hentoid.util.exception.UnsupportedContentException;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.image.ImageHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Helper for general newtworking
 */
public class DownloadHelper {

    private DownloadHelper() {
        throw new IllegalStateException("Utility class");
    }

    static final int DL_IO_BUFFER_SIZE_B = 50 * 1024; // NB : Actual size of read bytes may be smaller

    /**
     * Download the given resource to the given disk location
     *
     * @param site              Site to use params for
     * @param rawUrl            URL to download from
     * @param resourceId        ID of the corresponding resource (for logging purposes only)
     * @param requestHeaders    HTTP request headers to use
     * @param targetFolderUri   Uri of the folder where to save the downloaded resource
     * @param targetFileName    Name of the file to save the downloaded resource
     * @param forceMimeType     Forced mime-type of the downloaded resource (null for auto-set)
     * @param interruptDownload Used to interrupt the download whenever the value switches to true. If that happens, the file will be deleted.
     * @param notifyProgress    Consumer called with the download progress %
     * @return Pair containing
     * - Left : Uri of downloaded file
     * - Right : Detected mime-type of the downloaded resource
     * @throws IOException,UnsupportedContentException,DownloadInterruptedException if anything goes wrong
     */
    // TODO update doc
    public static ImmutablePair<Uri, String> downloadToFile(
            @NonNull Site site,
            @NonNull String rawUrl,
            int resourceId,
            List<Pair<String, String>> requestHeaders,
            @NonNull Uri targetFolderUri,
            @NonNull String targetFileName,
            String forceMimeType,
            boolean failFast,
            @NonNull final AtomicBoolean interruptDownload,
            Consumer<Float> notifyProgress) throws
            IOException, UnsupportedContentException, DownloadInterruptedException, IllegalStateException {
        Helper.assertNonUiThread();
        String url = HttpHelper.fixUrl(rawUrl, site.getUrl());

        if (interruptDownload.get())
            throw new DownloadInterruptedException("Download interrupted");

        Timber.d("DOWNLOADING %d %s", resourceId, url);
        Response response = failFast ?
                HttpHelper.getOnlineResourceFast(url, requestHeaders, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent()) :
                HttpHelper.getOnlineResourceDownloader(url, requestHeaders, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
        Timber.d("DOWNLOADING %d - RESPONSE %s", resourceId, response.code());
        if (response.code() >= 300)
            throw new NetworkingException(response.code(), "Network error " + response.code(), null);

        ResponseBody body = response.body();
        if (null == body)
            throw new IOException("Could not read response : empty body for " + url);

        long size = body.contentLength();
        if (size < 1) size = 1;

        String mimeType = StringHelper.protect(forceMimeType);

        Timber.d("WRITING DOWNLOAD %d TO %s/%s (size %.2f KB)", resourceId, targetFolderUri.getPath(), targetFileName, size / 1024.0);
        byte[] buffer = new byte[DL_IO_BUFFER_SIZE_B];
        final int notificationResolution = 250 * 1024 / DL_IO_BUFFER_SIZE_B; // Notify every 250 KB

        int len;
        long processed = 0;
        int iteration = 0;
        OutputStream out = null;
        Uri targetFileUri = null;
        try (InputStream in = body.byteStream()) {
            while ((len = in.read(buffer)) > -1) {
                if (interruptDownload.get()) break;
                processed += len;
                // Read mime-type on the fly if not forced
                if (0 == iteration++) {
                    if (mimeType.isEmpty()) {
                        mimeType = ImageHelper.INSTANCE.getMimeTypeFromPictureBinary(buffer);
                        if (mimeType.isEmpty() || mimeType.endsWith("/*")) {
                            String message = String.format(Locale.ENGLISH, "Invalid mime-type received from %s (size=%.2f)", url, size / 1024.0);
                            throw new UnsupportedContentException(message);
                        }
                    }
                    targetFileUri = createFile(targetFolderUri, targetFileName, mimeType);
                    out = FileHelper.getOutputStream(HentoidApp.getInstance(), targetFileUri);
                }

                if (len > 0) {
                    out.write(buffer, 0, len);

                    if (notifyProgress != null && 0 == iteration % notificationResolution)
                        notifyProgress.accept((processed * 100f) / size);

                    DownloadSpeedLimiter.INSTANCE.take(len);
                }
            }
            if (!interruptDownload.get()) {
                if (notifyProgress != null) notifyProgress.accept(100f);
                if (out != null) out.flush();
                if (targetFileUri != null) {
                    long targetFileSize = FileHelper.fileSizeFromUri(HentoidApp.getInstance(), targetFileUri);
                    Timber.d("DOWNLOAD %d [%s] WRITTEN TO %s (%.2f KB)", resourceId, mimeType, targetFileUri.getPath(), targetFileSize / 1024.0);
                }
                return new ImmutablePair<>(targetFileUri, mimeType);
            }
        } finally {
            body.close();
        }
        // Remove the remaining file chunk if download has been interrupted
        if (targetFileUri != null) FileHelper.removeFile(HentoidApp.getInstance(), targetFileUri);
        throw new DownloadInterruptedException("Download interrupted");
    }

    private static Uri createFile(@NonNull Uri targetFolderUri, @NonNull String targetFileName, @NonNull String mimeType) throws IOException {
        String targetFileNameFinal = targetFileName + "." + FileHelper.getExtensionFromMimeType(mimeType);
        // Keep the extension if the target file name is provided with one
        int dotOffset = targetFileName.lastIndexOf('.');
        if (dotOffset > -1) {
            int extLength = targetFileName.length() - targetFileName.lastIndexOf('.') - 1;
            if (extLength < 5) targetFileNameFinal = targetFileName;
        }
        if (ContentResolver.SCHEME_FILE.equals(targetFolderUri.getScheme())) {
            String path = targetFolderUri.getPath();
            if (path != null) {
                File targetFolder = new File(path);
                if (targetFolder.exists()) {
                    File targetFile = new File(targetFolder, targetFileNameFinal);
                    if (!targetFile.exists() && !targetFile.createNewFile()) {
                        throw new IOException("Could not create file " + targetFile.getPath() + " in " + path);
                    }
                    return Uri.fromFile(targetFile);
                } else {
                    throw new IOException("Could not create file " + targetFileNameFinal + " : " + path + " does not exist");
                }
            } else {
                throw new IOException("Could not create file " + targetFileNameFinal + " : " + targetFolderUri + " has no path");
            }
        } else {
            DocumentFile targetFolder = FileHelper.getDocumentFromTreeUriString(HentoidApp.getInstance(), targetFolderUri.toString());
            if (targetFolder != null) {
                DocumentFile file = FileHelper.findOrCreateDocumentFile(HentoidApp.getInstance(), targetFolder, mimeType, targetFileNameFinal);
                if (file != null) return file.getUri();
                else
                    throw new IOException("Could not create file " + targetFileNameFinal + " : creation failed");
            } else {
                throw new IOException("Could not create file " + targetFileNameFinal + " : " + targetFolderUri + " does not exist");
            }
        }
    }

    /**
     * Extract the given HTML document's canonical URL using link and OpenGraph metadata when available
     * NB : Uses the URL with the highest number when both exist and are not the same
     *
     * @param doc HTML document to parse
     * @return Canonical URL of the given document; empty string if nothing found
     */
    public static String getCanonicalUrl(@NonNull final Document doc) {
        // Get the canonical URL
        String canonicalUrl = "";
        Element canonicalElt = doc.select("head link[rel=canonical]").first();
        if (canonicalElt != null) canonicalUrl = canonicalElt.attr("href").trim();

        // Get the OpenGraph URL
        String ogUrl = "";
        Element ogUrlElt = doc.select("head meta[property=og:url]").first();
        if (ogUrlElt != null) ogUrl = ogUrlElt.attr("content").trim();

        final String finalUrl;
        if (!canonicalUrl.isEmpty() && !ogUrl.isEmpty() && !ogUrl.equals(canonicalUrl)) {
            String canonicalDigitsStr = StringHelper.keepDigits(canonicalUrl);
            int canonicalDigits = canonicalDigitsStr.isEmpty() ? 0 : Integer.parseInt(canonicalDigitsStr);
            String ogDigitsStr = StringHelper.keepDigits(ogUrl);
            int ogDigits = ogDigitsStr.isEmpty() ? 0 : Integer.parseInt(ogDigitsStr);
            finalUrl = (canonicalDigits > ogDigits) ? canonicalUrl : ogUrl;
        } else {
            if (!canonicalUrl.isEmpty()) finalUrl = canonicalUrl;
            else finalUrl = ogUrl;
        }
        return finalUrl;
    }

    public static StorageLocation selectDownloadLocation(@NonNull Context context) {
        String uriStr1 = Preferences.getStorageUri(StorageLocation.PRIMARY_1).trim();
        String uriStr2 = Preferences.getStorageUri(StorageLocation.PRIMARY_2).trim();

        // Obvious cases
        if (uriStr1.isEmpty() && uriStr2.isEmpty()) return StorageLocation.NONE;
        if (!uriStr1.isEmpty() && uriStr2.isEmpty()) return StorageLocation.PRIMARY_1;
        if (uriStr1.isEmpty()) return StorageLocation.PRIMARY_2;

        // Broken cases
        DocumentFile root1 = FileHelper.getDocumentFromTreeUriString(context, uriStr1);
        DocumentFile root2 = FileHelper.getDocumentFromTreeUriString(context, uriStr2);
        if (null == root1 && null == root2) return StorageLocation.NONE;
        if (root1 != null && null == root2) return StorageLocation.PRIMARY_1;
        if (null == root1) return StorageLocation.PRIMARY_2;

        // Apply download strategy
        FileHelper.MemoryUsageFigures memUsage1 = new FileHelper.MemoryUsageFigures(context, root1);
        FileHelper.MemoryUsageFigures memUsage2 = new FileHelper.MemoryUsageFigures(context, root2);

        int strategy = Preferences.getStorageDownloadStrategy();
        if (Preferences.Constant.STORAGE_FILL_FALLOVER == strategy) {
            if (100 - memUsage1.getFreeUsageRatio100() > Preferences.getStorageSwitchThresholdPc())
                return StorageLocation.PRIMARY_2;
            else return StorageLocation.PRIMARY_1;
        } else {
            if (memUsage1.getfreeUsageBytes() > memUsage2.getfreeUsageBytes())
                return StorageLocation.PRIMARY_1;
            else return StorageLocation.PRIMARY_2;
        }
    }
}
