package me.devsaki.hentoid.util.download;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

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

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.image.ImageHelper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.exception.DownloadInterruptedException;
import me.devsaki.hentoid.util.exception.UnsupportedContentException;
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

    /**
     * Download the given resource to the given disk location
     *
     * @param site              Site to use params for
     * @param url               URL to download from
     * @param resourceId        ID of the corresponding resource (for logging purposes only)
     * @param requestHeaders    HTTP request headers to use
     * @param targetFolder      Folder where to save the downloaded resource
     * @param targetFileName    Name of the file to save the downloaded resource
     * @param forceMimeType     Forced mime-type of the downloaded resource (null for auto-set)
     * @param interruptDownload Used to interrupt the download whenever the value switches to true. If that happens, the file will be deleted.
     * @param notifyProgress    Consumer called with the download progress %
     * @return Pair containing
     * - Left : Downloaded file
     * - Right : Detected mime-type of the downloades resource
     * @throws IOException,UnsupportedContentException,DownloadInterruptedException if anything goes wrong
     */
    public static ImmutablePair<File, String> downloadToFile(
            @NonNull Site site,
            @NonNull String url,
            int resourceId,
            List<Pair<String, String>> requestHeaders,
            @NonNull File targetFolder,
            @NonNull String targetFileName,
            String forceMimeType,
            @NonNull final AtomicBoolean interruptDownload,
            Consumer<Float> notifyProgress) throws
            IOException, UnsupportedContentException, DownloadInterruptedException {

        if (interruptDownload.get())
            throw new DownloadInterruptedException("Download interrupted");

        Timber.d("CACHE DOWNLOADING %d %s", resourceId, url);
        Response response = HttpHelper.getOnlineResourceFast(url, requestHeaders, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
        Timber.d("CACHE DOWNLOADING %d - RESPONSE %s", resourceId, response.code());
        if (response.code() >= 300) throw new IOException("Network error " + response.code());

        ResponseBody body = response.body();
        if (null == body)
            throw new IOException("Could not read response : empty body for " + url);

        long size = body.contentLength();
        if (size < 1) size = 1;

        String mimeType = StringHelper.protect(forceMimeType);

        File targetFile = new File(targetFolder, targetFileName);
        if (!targetFile.exists() && !targetFile.createNewFile())
            throw new IOException("Could not create file " + targetFile.getPath());

        Timber.d("WRITING CACHED DOWNLOAD %d TO %s (size %.2f KB)", resourceId, targetFile.getAbsolutePath(), size / 1024.0);
        byte[] buffer = new byte[FileHelper.FILE_IO_BUFFER_SIZE];
        int len;
        long processed = 0;
        int iteration = 0;
        try (InputStream in = body.byteStream(); OutputStream out = FileHelper.getOutputStream(targetFile)) {
            while ((len = in.read(buffer)) > -1) {
                if (interruptDownload.get()) break;
                processed += len;
                // Read mime-type on the fly if not forced
                if (0 == iteration && mimeType.isEmpty()) {
                    mimeType = ImageHelper.getMimeTypeFromPictureBinary(buffer);
                    if (mimeType.isEmpty() || mimeType.equals(ImageHelper.MIME_IMAGE_GENERIC)) {
                        String message = String.format(Locale.ENGLISH, "Invalid mime-type received from %s (size=%.2f)", url, size / 1024.0);
                        throw new UnsupportedContentException(message);
                    }
                }
                if (notifyProgress != null && 0 == ++iteration % 50) // Notify every 200KB
                    notifyProgress.accept((processed * 100f) / size);
                out.write(buffer, 0, len);
            }
            if (!interruptDownload.get()) {
                if (notifyProgress != null) notifyProgress.accept(100f);
                out.flush();
                Timber.d("CACHED DOWNLOAD %d [%s] WRITTEN TO %s (%.2f KB)", resourceId, mimeType, targetFile.getAbsolutePath(), targetFile.length() / 1024.0);
                return new ImmutablePair<>(targetFile, mimeType);
            }
        }
        // Remove the remaining file chunk if download has been interrupted
        FileHelper.removeFile(targetFile);
        throw new DownloadInterruptedException("Download interrupted");
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
            int canonicalDigits = Integer.parseInt(StringHelper.keepDigits(canonicalUrl));
            int ogDigits = Integer.parseInt(StringHelper.keepDigits(ogUrl));
            finalUrl = (canonicalDigits > ogDigits) ? canonicalUrl : ogUrl;
        } else {
            if (!canonicalUrl.isEmpty()) finalUrl = canonicalUrl;
            else finalUrl = ogUrl;
        }
        return finalUrl;
    }
}
