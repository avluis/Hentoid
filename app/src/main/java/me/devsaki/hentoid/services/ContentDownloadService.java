package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Intent;
import android.util.Pair;

import com.android.volley.Request;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.parsers.ContentParser;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.NetworkStatus;
import timber.log.Timber;

public class ContentDownloadService extends IntentService {

    private HentoidDB db;
    private NotificationPresenter notificationPresenter;

    public ContentDownloadService() {
        super(ContentDownloadService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = HentoidDB.getInstance(this);

        notificationPresenter = new NotificationPresenter();
        EventBus.getDefault().register(notificationPresenter);

        Timber.d("Download service created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(notificationPresenter);
        notificationPresenter = null;

        Timber.d("Download service destroyed");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!NetworkStatus.isOnline(this)) {
            Timber.w("No connection!");
            return;
        }

        downloadQueueHead();
    }

    private void downloadQueueHead()
    {
        // Exits if download queue is already running - there can only be one service active at a time
        if (!QueueManager.getInstance(this).isQueueEmpty()) return;

        // Works on first item of queue
        List<Pair<Integer,Integer>> queue = db.selectQueue();
        if (0 == queue.size())
        {
            Timber.w("Queue is empty");
            return;
        }
        Content content = db.selectContentById(queue.get(0).first);

        if (content != null && content.getStatus() != StatusContent.DOWNLOADED) {
            int nbDownloadedImages = 0;

            // Check if images are already known
            List<ImageFile> images = content.getImageFiles();
            if (images != null && images.size() > 0)
            {
                // TODO - do something ?
            } else { // Create image list in DB
                images = parseImageFiles(content);
                content.setImageFiles(images);
                db.insertImageFiles(content);
            }

            if (0 == images.size())
            {
                Timber.w("Image list is empty");
                return;
            }

            File dir = FileHelper.getContentDownloadDir(this, content);
            Timber.d("Directory created: %s", FileHelper.createDirectory(dir));

            // Plan download actions
            for (ImageFile img : images) {
                if (img.getStatus().equals(StatusContent.SAVED) || img.getStatus().equals(StatusContent.ERROR)) QueueManager.getInstance(this).addToRequestQueue(buildStringRequest(img, dir, content.getId()));
                else if (img.getStatus().equals(StatusContent.DOWNLOADED)) nbDownloadedImages++;
            }

            updateActivity(nbDownloadedImages/images.size());

            /*
            content.setStatus(StatusContent.DOWNLOADED);
            db.updateContentStatus(content);
            */
        }
    }

    private static List<ImageFile> parseImageFiles(Content content) {
        ContentParser parser = ContentParserFactory.getInstance().getParser(content);
        List<String> aUrls = parser.parseImageList(content);

        int i = 1;
        List<ImageFile> imageFileList = new ArrayList<>();
        for (String str : aUrls) {
            String name = String.format(Locale.US, "%03d", i);
            imageFileList.add(new ImageFile()
                    .setUrl(str)
                    .setOrder(i++)
                    .setStatus(StatusContent.SAVED)
                    .setName(name));
        }

        return imageFileList;
    }

    private InputStreamVolleyRequest buildStringRequest(ImageFile img, File dir, int contentId)
    {
        return new InputStreamVolleyRequest(Request.Method.GET, img.getUrl(),
                response -> {
                    // TODO handle the response
                    try {
                        Timber.d("xxxResponse %s", img.getUrl());
                        if (response!=null) {
                            //covert reponse to input stream

                            try (InputStream input = new ByteArrayInputStream(response)) {
                                //Create a file on desired path and write stream data to it
                                File file = new File(dir, Math.random() + ".jpg");
                                //                                map.put("resume_path", file.toString());
                                Timber.d("xxxWriteTo %s", file.getPath());
                                try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
                                    byte data[] = new byte[1024];

                                    long total = 0;
                                    int count;

                                    while ((count = input.read(data)) != -1) {
                                        total += count;
                                        output.write(data, 0, count);
                                    }

                                    output.flush();
                                }
                            }

                            finalizeImage(img, dir, contentId);
                        }
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        Timber.d("KEY_ERROR", "UNABLE TO DOWNLOAD FILE");
                        e.printStackTrace();
                    }
                }, error -> {
                    // TODO handle the error
            Timber.d("xxxError");
                    error.printStackTrace();
                }, null);
    }

    void finalizeImage(ImageFile img, File dir, int contentId)
    {
        img.setStatus(StatusContent.DOWNLOADED);
        db.updateImageFileStatus(img);

        double dlRate = db.countProcessedImageRateById(contentId);
        updateActivity(dlRate);

        if (1 == dlRate)
        {
            Content content = db.selectContentById(contentId);

            // Save JSON file
            try {
                JsonHelper.saveJson(content, dir);
            } catch (IOException e) {
                Timber.e(e, "Error saving JSON: %s", content.getTitle());
            }

            // Signal activity end
            HentoidApp.downloadComplete();
            updateActivity(-1);
            Timber.d("Content download finished: %s", content.getTitle());

            // Tracking Event (Download Completed)
            HentoidApp.getInstance().trackEvent(DownloadService.class, "Download", "Download Content: Complete");

            // Mark content as downloaded
            content.setDownloadDate(new Date().getTime());
            content.setStatus(StatusContent.DOWNLOADED);
            db.updateContentStatus(content);

            // Delete from queue
            db.deleteQueueById(contentId);

            // Download next content
            // TODO - launch in a new Intent ??
            downloadQueueHead();
        }
    }

    private void updateActivity(double percent) {
        EventBus.getDefault().post(new DownloadEvent(percent));
    }
}
