package me.devsaki.hentoid.util;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;

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
    private static final String KEY_PACK_URL = "packURL";
    private static final String KEY_VERSION_CODE = "versionCode";
    private static final int KEY_CACHE_VERSION = 1;

    private static int getCacheVer() {
        return KEY_CACHE_VERSION;
    }

    public static boolean init(Context cxt) {
        File cacheDir = cxt.getCacheDir();

        if (cacheDir != null) {
            LogHelper.d(TAG, "Cache Dir: " + cacheDir);

            File cacheTracker = new File(cacheDir + "/" + "cache.json");

            if (!cacheTracker.exists()) {
                LogHelper.d(TAG, "Cache Tracking file does not exist!");
                // TODO: Check remote cache version
                if (NetworkStatus.isOnline(cxt)) {
                    LogHelper.d(TAG, "Checking remote cache version.");
                    new UpdateCheckTask().execute(CACHE_JSON);
                } else {
                    LogHelper.w(TAG, "Network is not connected!");
                    // TODO: Unpack current version instead
                }

                // TODO: If bundledCacheVersion < remoteCacheVersion: Download cache pack

                // TODO: If remote cache pack unreachable (GitHub down?), use bundled cache

                // TODO: Unpack cache pack
            } else {
                LogHelper.d(TAG, "Cache Tracking file found.");
                // TODO: Check cache version
                int cacheVersion = getCacheVer();

                if (KEY_CACHE_VERSION < cacheVersion) {
                    LogHelper.d(TAG, "Saved Cache Version: " + KEY_CACHE_VERSION +
                            " - Current Cache Version: " + cacheVersion);
                    // TODO: Grab latest version (or delete current and re-run init)
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static class UpdateCheckTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            try {
                JSONObject jsonObject = downloadURL(params[0]);
                if (jsonObject != null) {
                    int cacheVersionCode = jsonObject.getInt(KEY_VERSION_CODE);
                    if (KEY_CACHE_VERSION < cacheVersionCode) {
                        LogHelper.d(TAG, "Bundled Cache is outdated.");
                        String downloadURL = jsonObject.getString(KEY_PACK_URL);
                        LogHelper.d(TAG, "Cache Pack URL: " + downloadURL);
                    } else {
                        LogHelper.d(TAG, "Bundled Cache is same as current.");
                    }
                }
            } catch (IOException e) {
                LogHelper.e(TAG, "IO ERROR: ", e);
                HentoidApp.getInstance().trackException(e);
                // TODO: Handle 404: File not found
            } catch (JSONException e) {
                HentoidApp.getInstance().trackException(e);
                LogHelper.e(TAG, "Error with JSON File: ", e);
            }

            return null;
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
}
