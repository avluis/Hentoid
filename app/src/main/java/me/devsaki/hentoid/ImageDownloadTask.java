package me.devsaki.hentoid;

import android.util.Log;
import android.webkit.CookieManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;

import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.Helper;

/**
 * Created by Shiro on 2/5/2016.
 * Callable for downloading images asynchronously
 * TODO: Handle errors internally for encapsulation instead of throwing Exceptions
 */
public class ImageDownloadTask implements Callable<Void> {

    private static final String TAG = ImageDownloadTask.class.getName();
    private static final int BUFFER_SIZE = 10 * 1024;
    private File dir;
    private String filename;
    private String imageUrl;
    private ImageDownloadBatch.Observer observer = null;

    public ImageDownloadTask(File dir, String filename, String imageUrl) {
        this.dir = dir;
        this.filename = filename;
        this.imageUrl = imageUrl;
    }

    public ImageDownloadTask registerObserver(ImageDownloadBatch.Observer observer) {
        this.observer = observer;
        return this;
    }

    @Override
    public Void call() throws IOException {
        if (observer != null) observer.taskStarted();
        Log.i(TAG, "Starting download " + imageUrl);

        OutputStream output = null;
        InputStream input = null;
        File file = null;

        try {
            URL url = new URL(imageUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(10000);
            urlConnection.setRequestProperty("User-Agent", Constants.USER_AGENT);
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if (!cookies.isEmpty()) {
                Helper.setSessionCookie(cookies);
                urlConnection.setRequestProperty("Cookie", cookies);
            } else {
                urlConnection.setRequestProperty("Cookie", Helper.getSessionCookie());
            }

            final int responseCode = urlConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                //May be a non-fatal error
                Log.e(TAG, "Error in http response: "
                        + responseCode + " - "
                        + urlConnection.getResponseMessage());
            }

            switch (urlConnection.getHeaderField("Content-Type")) {
                case "image/png":
                    file = new File(dir, filename + ".png");
                    break;
                case "image/gif":
                    file = new File(dir, filename + ".gif");
                    break;
                default:
                    file = new File(dir, filename + ".jpg");
                    break;
            }

            if (file.exists()) {
                urlConnection.disconnect();
                return null;
            }

            input = urlConnection.getInputStream();

            output = new FileOutputStream(file);

            byte[] buffer = new byte[BUFFER_SIZE];
            int dataLength;
            while ((dataLength = input.read(buffer, 0, BUFFER_SIZE)) != -1) {
                output.write(buffer, 0, dataLength);
            }
            output.flush();
            urlConnection.disconnect();

        } catch (Exception e) {
            if (file != null) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            throw e;
        } finally {
            if (output != null) {
                output.close();
            }
            if (input != null) {
                input.close();
            }
        }
        Log.i(TAG, "Done downloading " + imageUrl);
        if (observer != null) observer.taskFinished();

        return null;
    }
}
