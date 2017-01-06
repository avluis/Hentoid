package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;

import com.thin.downloadmanager.DownloadManager;
import com.thin.downloadmanager.DownloadRequest;
import com.thin.downloadmanager.DownloadStatusListenerV1;
import com.thin.downloadmanager.ThinDownloadManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import me.devsaki.hentoid.HentoidApp;

/**
 * Created by avluis on 07/22/2016.
 * Assets Cache Management Util
 */
public class AssetsCache {
    private static final String TAG = LogHelper.makeLogTag(AssetsCache.class);

    private static final String CACHE_JSON =
            "https://raw.githubusercontent.com/AVnetWS/Hentoid/master/.cache/cache.json";
    private static final String CACHE_PACK = "cache.zip";
    private static final String KEY_PACK_URL = "packURL";
    private static final String KEY_VERSION_CODE = "versionCode";
    // TODO: Revert cache version before publishing app update (same goes for cache.json)
    private static final int BUNDLED_CACHE_VERSION = 2;
    private static AssetManager assetManager;
    private static File cacheDir;

    public static void init(Context cxt) {
        assetManager = cxt.getAssets();
        cacheDir = cxt.getExternalCacheDir();
        if (cacheDir != null) {
            // Check remote cache version
            checkNetworkConnectivity();
        } else {
            LogHelper.d(TAG, "Cache INIT Failed!");
        }
    }

    private static void checkNetworkConnectivity() {
        AsyncTask.execute(() -> {
            boolean connected = NetworkStatus.hasInternetAccess(HentoidApp.getAppContext());

            if (connected) {
                LogHelper.d(TAG, "Checking remote cache version.");
                new UpdateCheckTask().execute(CACHE_JSON);
            } else {
                LogHelper.w(TAG, "Network is not connected!");
                unpackBundle();
            }
        });
    }

    private static void downloadCachePack(String downloadURL) {
        // Clean up cache directory
        LogHelper.d(TAG, "Directory cleaned successfully: " + FileHelper.cleanDirectory(cacheDir));
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
            LogHelper.e(TAG, e, "Failed to assemble file from assets");
        }
    }

    private static void extractFile(File file) {
        LogHelper.d(TAG, "Extracting cache files.");
        String zipFile = file.getAbsolutePath();
        String destinationPath = cacheDir.getPath();
        new ZipUtil.UnZipTask().execute(zipFile, destinationPath);
    }

    private static class UpdateCheckTask extends AsyncTask<String, Void, Void> {
        int remoteCacheVersion = -1;
        String downloadURL;

        @Override
        protected Void doInBackground(String... params) {
            try {
                JSONObject jsonObject = new JsonHelper().jsonReader(params[0]);
                if (jsonObject != null) {
                    remoteCacheVersion = jsonObject.getInt(KEY_VERSION_CODE);
                    downloadURL = jsonObject.getString(KEY_PACK_URL);
                } else {
                    LogHelper.w(TAG, "JSON response was null!");
                    unpackBundle();
                }
            } catch (IOException e) {
                LogHelper.e(TAG, e, "IO ERROR");
                unpackBundle();
            } catch (JSONException e) {
                LogHelper.e(TAG, e, "Error with JSON File");
                unpackBundle();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            LogHelper.d(TAG, "Remote cache version: " + remoteCacheVersion);
            if (remoteCacheVersion >= 1) {
                if (BUNDLED_CACHE_VERSION < remoteCacheVersion) {
                    LogHelper.d(TAG, "Bundled cache is outdated.");
                    downloadCachePack(downloadURL);
                } else {
                    LogHelper.d(TAG, "Bundled cache is current.");
                    unpackBundle();
                }
            }
        }
    }
}
