package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;

import com.thin.downloadmanager.DownloadManager;
import com.thin.downloadmanager.DownloadRequest;
import com.thin.downloadmanager.DownloadStatusListenerV1;
import com.thin.downloadmanager.ThinDownloadManager;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.net.ssl.HttpsURLConnection;

import me.devsaki.hentoid.HentoidApp;

/**
 * Created by avluis on 07/22/2016.
 * Assets Cache Management Util
 */
public class AssetsCache {
    private static final String TAG = LogHelper.makeLogTag(AssetsCache.class);

    private static final String CACHE_JSON =
            "https://raw.githubusercontent.com/AVnetWS/Hentoid/new-sources/.cache/cache.json";
    private static final String CACHE_PACK = "cache.zip";
    private static final String KEY_PACK_URL = "packURL";
    private static final String KEY_VERSION_CODE = "versionCode";
    private static final int BUNDLED_CACHE_VERSION = 1;
    private static AssetManager assetManager;
    private static File cacheDir;

    public static void init(Context cxt) {
        assetManager = cxt.getAssets();
        cacheDir = cxt.getExternalCacheDir();
        if (cacheDir != null) {
            LogHelper.d(TAG, "Cache Dir: " + cacheDir);

            // Check remote cache version
            if (NetworkStatus.isOnline(cxt)) {
                LogHelper.d(TAG, "Checking remote cache version.");
                new UpdateCheckTask().execute(CACHE_JSON);
            } else {
                LogHelper.w(TAG, "Network is not connected!");
                unpackBundle();
            }
        } else {
            // TODO: Handle inaccessible cache dir
            LogHelper.d(TAG, "Cache INIT Failed!");
        }
    }

    private static void downloadCachePack(String downloadURL) {
        // Clean up cache directory
        Helper.cleanDir(cacheDir);
        // Download cache pack
        Uri downloadUri = Uri.parse(downloadURL);
        final Uri destinationUri = Uri.parse(cacheDir + "/" + CACHE_PACK);

        DownloadRequest request = new DownloadRequest(downloadUri)
                .setDestinationURI(destinationUri)
                .setPriority(DownloadRequest.Priority.HIGH)
                .setStatusListener(new DownloadStatusListenerV1() {
                    @Override
                    public void onDownloadComplete(DownloadRequest downloadRequest) {
                        // Unpack cache file
                        File file = new File(String.valueOf(destinationUri));
                        LogHelper.d(TAG, "Downloaded cache file: " + file.getAbsolutePath());
                        extractFile(file);
                    }

                    @Override
                    public void onDownloadFailed(DownloadRequest downloadRequest, int errorCode,
                                                 String errorMessage) {
                        unpackBundle();
                    }

                    @Override
                    public void onProgress(DownloadRequest downloadRequest, long totalBytes,
                                           long downloadedBytes, int progress) {
                        // Not listening
                    }
                });

        DownloadManager downloadManager = new ThinDownloadManager();
        LogHelper.d(TAG, "Download Request ID: " + downloadManager.add(request));
    }

    private static void unpackBundle() {
        InputStream inputStream;
        File file;
        try {
            inputStream = assetManager.open(CACHE_PACK);
            file = new File(cacheDir + "/" + CACHE_PACK);
            OutputStream outputStream = new FileOutputStream(file);

            byte buffer[] = new byte[1024];
            int length;

            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.close();
            inputStream.close();

            extractFile(file);
        } catch (IOException e) {
            LogHelper.e(TAG, "Failed to assemble file from assets", e);
        }
    }

    private static void extractFile(File file) {
        LogHelper.d(TAG, "Extracting files.");
        String zipFile = file.getAbsolutePath();
        LogHelper.d(TAG, "File: " + zipFile);
        String destinationPath = cacheDir.getPath();
        LogHelper.d(TAG, "Path: " + destinationPath);

        new UnZipTask().execute(zipFile, destinationPath);
    }

    private static class UpdateCheckTask extends AsyncTask<String, Void, Void> {
        int remoteCacheVersion = -1;
        String downloadURL;

        @Override
        protected Void doInBackground(String... params) {
            try {
                JSONObject jsonObject = downloadURL(params[0]);
                if (jsonObject != null) {
                    remoteCacheVersion = jsonObject.getInt(KEY_VERSION_CODE);
                    downloadURL = jsonObject.getString(KEY_PACK_URL);
                } else {
                    LogHelper.w(TAG, "JSON response was null!");
                    unpackBundle();
                }
            } catch (IOException e) {
                LogHelper.e(TAG, "IO ERROR: ", e);
                HentoidApp.getInstance().trackException(e);
                unpackBundle();
            } catch (JSONException e) {
                LogHelper.e(TAG, "Error with JSON File: ", e);
                HentoidApp.getInstance().trackException(e);
                unpackBundle();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            LogHelper.d(TAG, "Remote Cache Version: " + remoteCacheVersion);
            if (remoteCacheVersion >= 1) {
                if (BUNDLED_CACHE_VERSION < remoteCacheVersion) {
                    LogHelper.d(TAG, "Bundled Cache is outdated.");
                    LogHelper.d(TAG, "Cache Pack URL: " + downloadURL);
                    downloadCachePack(downloadURL);
                } else {
                    LogHelper.d(TAG, "Bundled Cache is same as current.");
                    unpackBundle();
                }
            }
        }

        JSONObject downloadURL(String updateURL) throws IOException {
            InputStream inputStream = null;
            try {
                disableConnectionReuse();
                URL url = new URL(updateURL);
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setReadTimeout(10000);
                connection.setConnectTimeout(15000);
                connection.setRequestMethod("GET");
                connection.setDoInput(true);

                connection.connect();
                int response = connection.getResponseCode();

                LogHelper.d(TAG, "HTTP Response: " + response);

                inputStream = connection.getInputStream();
                String contentString = readInputStream(inputStream);

                return new JSONObject(contentString);
            } catch (JSONException e) {
                HentoidApp.getInstance().trackException(e);
                LogHelper.e(TAG, "JSON file not properly formatted: ", e);
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }

            return null;
        }

        private void disableConnectionReuse() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
                System.setProperty("http.keepAlive", "false");
            }
        }

        private String readInputStream(InputStream inputStream) throws IOException {
            StringBuilder stringBuilder = new StringBuilder(inputStream.available());
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream,
                    Charset.forName("UTF-8")));
            String line = reader.readLine();

            while (line != null) {
                stringBuilder.append(line);
                line = reader.readLine();
            }

            return stringBuilder.toString();
        }
    }

    private static class UnZipTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String filePath = params[0];
            String destinationPath = params[1];

            File archive = new File(filePath);
            try {
                ZipFile zipfile = new ZipFile(archive);
                for (Enumeration e = zipfile.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = (ZipEntry) e.nextElement();
                    unzipEntry(zipfile, entry, destinationPath);
                }
                zipfile.close();
            } catch (Exception e) {
                LogHelper.e(TAG, "Error while extracting file " + archive, e);
                return false;
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            LogHelper.d(TAG, "All files extracted: " + aBoolean);
        }

        private void unzipEntry(ZipFile zipfile, ZipEntry entry,
                                String outputDir) throws IOException {

            if (entry.isDirectory()) {
                createDir(new File(outputDir, entry.getName()));
                return;
            }

            File outputFile = new File(outputDir, entry.getName());
            if (!outputFile.getParentFile().exists()) {
                createDir(outputFile.getParentFile());
            }

            LogHelper.d(TAG, "Extracting: " + entry);
            BufferedInputStream inputStream = new BufferedInputStream(
                    zipfile.getInputStream(entry));
            BufferedOutputStream outputStream = new BufferedOutputStream(
                    new FileOutputStream(outputFile));

            //noinspection TryFinallyCanBeTryWithResources
            try {
                IOUtils.copy(inputStream, outputStream);
            } finally {
                outputStream.close();
                inputStream.close();
            }
        }

        private void createDir(File dir) {
            if (dir.exists()) {
                return;
            }
            LogHelper.d(TAG, "Creating dir " + dir.getName());
            if (!dir.mkdirs()) {
                throw new RuntimeException("Can not create dir " + dir);
            }
        }
    }
}
