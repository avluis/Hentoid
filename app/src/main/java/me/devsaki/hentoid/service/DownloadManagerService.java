package me.devsaki.hentoid.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import me.devsaki.hentoid.DownloadManagerActivity;
import me.devsaki.hentoid.DownloadsActivity;
import me.devsaki.hentoid.MainActivity;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.FakkuDroidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.enums.Status;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpClientHelper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

public class DownloadManagerService extends IntentService {

    private static final String TAG = DownloadManagerService.class.getName();
    public static final String INTENT_PERCENT_BROADCAST = "broadcast_percent";
    public static final String NOTIFICATION = "me.devsaki.hentoid.service";

    private NotificationManager notificationManager;
    private FakkuDroidDB db;
    public static boolean paused;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");

        db = new FakkuDroidDB(this);
        notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    public DownloadManagerService() {
        super(DownloadManagerService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Content content = db.selectContentByStatus(Status.DOWNLOADING);
        if(content==null||content.getStatus()==Status.DOWNLOADED||content.getStatus()==Status.ERROR)
            return;

        if(paused){
            paused = false;
            return;
        }

        content.setImageFiles(new ArrayList<ImageFile>());
        try {
            URL url = new URL(Constants.FAKKU_URL + content.getUrl() + Constants.FAKKU_READ);
            String site = null;
            String extention = null;
            String html = HttpClientHelper.call(url);

            String find = "imgpath(x)";
            int indexImgpath = html.indexOf(find) + find.length();
            if(indexImgpath==find.length()-1){
                throw new RuntimeException("Not find image url.");
            }
            find = "return '";
            int indexReturn = html.indexOf(find, indexImgpath) + find.length();
            find = "'";
            int indexFinishSite = html.indexOf(find, indexReturn);
            site = html.substring(indexReturn, indexFinishSite);
            if(site.startsWith("//")){
                site = "https:" + site;
            }
            int indexStartExtension = html.indexOf(find, indexFinishSite + find.length()) + find.length();
            int indexFinishExtension = html.indexOf(find, indexStartExtension);
            extention = html.substring(indexStartExtension, indexFinishExtension);
            for (int i = 1; i <= content.getQtyPages(); i++) {
                String name = String.format("%03d", i) + extention;
                ImageFile imageFile = new ImageFile();
                imageFile.setUrl(site + name);
                imageFile.setOrder(i);
                imageFile.setStatus(Status.SAVED);
                imageFile.setName(name);
                content.getImageFiles().add(imageFile);
            }

        } catch (Exception e) {
            Log.e(TAG, "Guessing extension");
            String urlCdn = content.getCoverImageUrl().substring(2, content.getCoverImageUrl().lastIndexOf("/thumbs/")) + "/images/";
            for (int i = 1; i <= content.getQtyPages(); i++) {
                String name = String.format("%03d", i) + ".jpg";
                ImageFile imageFile = new ImageFile();
                imageFile.setUrl(urlCdn + name);
                imageFile.setOrder(i);
                imageFile.setStatus(Status.SAVED);
                imageFile.setName(name);
                content.getImageFiles().add(imageFile);
            }
        }
        db.insertImageFiles(content);

        if(paused){
            paused = false;
            content = db.selectContentById(content.getId());
            showNotification(0, content);
            return;
        }

        Log.i(TAG, "Start Download Content : " + content.getTitle());

        boolean error = false;
        //Directory
        File dir = Helper.getDownloadDir(content.getFakkuId(), DownloadManagerService.this);
        try {
            //Download Cover Image
            Helper.saveInStorage(new File(dir, "thumb.jpg"), content.getCoverImageUrl());
        } catch (Exception e) {
            Log.e(TAG, "Error Saving cover image " + content.getTitle(), e);
            error = true;
        }


        showNotification(0, content);
        int count = 0;
        for (ImageFile imageFile : content.getImageFiles()) {
            if(paused){
                paused = false;
                content = db.selectContentById(content.getId());
                showNotification(0, content);
                if(content.getStatus()==Status.SAVED){
                    try {
                        FileUtils.deleteDirectory(dir);
                    } catch (IOException e) {
                        Log.e(TAG, "error deleting content directory", e);
                    }
                }
                return;
            }
            boolean imageFileErrorDownload = false;
            try {
                if (imageFile.getStatus() != Status.IGNORED) {
                    Helper.saveInStorage(new File(dir, imageFile.getName()), imageFile.getUrl());
                    Log.i(TAG, "Download Image File (" + imageFile.getName() + ") / " + content.getTitle());
                }
                count++;
                double percent = count*100.0/content.getImageFiles().size();
                showNotification(percent, content);
                updateActivity(percent);
            } catch (Exception ex) {
                Log.e(TAG, "Error Saving Image File (" + imageFile.getName() + ") " + content.getTitle(), ex);
                error = true;
                imageFileErrorDownload=true;
            }
            if (imageFileErrorDownload) {
                imageFile.setStatus(Status.ERROR);
            } else {
                imageFile.setStatus(Status.DOWNLOADED);
            }
            db.updateImageFileStatus(imageFile);
        }
        db.updateContentStatus(content);
        content.setDownloadDate(new Date().getTime());
        if (error) {
            content.setStatus(Status.ERROR);
        } else {
            content.setStatus(Status.DOWNLOADED);
        }
        //Save JSON file
        try {
            Helper.saveJson(content, dir);
        } catch (IOException e) {
            Log.e(TAG, "Error Save JSON " + content.getTitle(), e);
        }
        db.updateContentStatus(content);
        Log.i(TAG, "Finish Download Content : " + content.getTitle());
        showNotification(0, content);
        updateActivity(-1);
        content = db.selectContentByStatus(Status.DOWNLOADING);
        if(content!=null){
            Intent intentService = new Intent(Intent.ACTION_SYNC, null, this, DownloadManagerService.class);
            intentService.putExtra("content_id", content.getId());
            startService(intentService);
        }
    }

    private void updateActivity(double percent){
        Intent intent = new Intent(NOTIFICATION);
        intent.putExtra(INTENT_PERCENT_BROADCAST, percent);
        sendBroadcast(intent);
    }

    private void showNotification(double percent, Content content) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
                DownloadManagerService.this).setSmallIcon(
                R.drawable.ic_fakkudroid_launcher).setContentTitle(content.getTitle());

        Intent resultIntent = null;
        if(content.getStatus()==Status.DOWNLOADED||content.getStatus()==Status.ERROR){
            resultIntent= new Intent(DownloadManagerService.this,
                    DownloadsActivity.class);
        }else if(content.getStatus()==Status.DOWNLOADING||content.getStatus()==Status.PAUSED){
            resultIntent= new Intent(DownloadManagerService.this,
                    DownloadManagerActivity.class);
        }else if(content.getStatus()==Status.SAVED){
            resultIntent = new Intent(DownloadManagerService.this,
                    MainActivity.class);
            resultIntent.putExtra("url", content.getUrl());
        }

        // Adds the Intent to the top of the stack
        // Gets a PendingIntent containing the entire back stack
        PendingIntent resultPendingIntent = PendingIntent.getActivity(DownloadManagerService.this,
                0, resultIntent, PendingIntent.FLAG_ONE_SHOT);


        if (content.getStatus()==Status.DOWNLOADING) {
            mBuilder.setContentText(getResources().getString(R.string.downloading)
                    + String.format("%.2f", percent) + "%");
            mBuilder.setProgress(100, (int)percent, false);
        } else {
            int resource = 0;
            if(content.getStatus()==Status.DOWNLOADED){
                resource = R.string.download_completed;
            }else if(content.getStatus()==Status.PAUSED){
                resource = R.string.download_paused;
            }else if(content.getStatus()==Status.SAVED){
                resource = R.string.download_cancelled;
            }else if(content.getStatus()==Status.ERROR){
                resource = R.string.download_error;
            }
            mBuilder.setContentText(getResources().getString(resource));
            mBuilder.setProgress(0, 0, false);
        }
        Notification notif = mBuilder.build();
        notif.contentIntent = resultPendingIntent;
        if (percent > 0)
            notif.flags = Notification.FLAG_ONGOING_EVENT;
        else
            notif.flags = notif.flags | Notification.DEFAULT_LIGHTS
                    | Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(content.getId(), notif);
    }
}
