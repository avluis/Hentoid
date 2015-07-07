package me.devsaki.hentoid.asynctasks;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.dto.UserRequest;
import me.devsaki.hentoid.dto.VersionDto;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.NetworkStatus;

/**
 * Created by neko on 17/06/2015.
 */
public class UpdateCheckerTask extends AsyncTask<String, Void, VersionDto> {

    private static final String TAG = UpdateCheckerTask.class.getName();
    private Context mContext;
    private VersionDto versionDto;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder mBuilder;

    public UpdateCheckerTask(Context mContext) {
        this.mContext = mContext;
        notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    protected void onPreExecute() {
        this.versionDto = null;
        Toast.makeText(mContext, R.string.looking_new_versions, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected VersionDto doInBackground(String... params) {
        try {
            PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            UserRequest userRequest = new UserRequest();
            String androidId = Settings.Secure.getString(mContext.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            userRequest.setIdDevice(androidId);
            userRequest.setManufacturer(Build.MANUFACTURER);
            userRequest.setModel(Build.MODEL);
            userRequest.setAppVersionCode(pInfo.versionCode);
            userRequest.setAppVersionName(pInfo.versionName);
            userRequest.setAndroidVersionCode(Build.VERSION.SDK_INT);
            userRequest.setAndroidVersionName(Build.VERSION.RELEASE);
            return HttpClientHelper.checkLastVersion(userRequest);
        } catch (Exception ex) {
            Log.e(TAG, "update checker asynctask", ex);
        }
        return null;
    }

    @Override
    protected void onPostExecute(VersionDto result) {
        if (NetworkStatus.getInstance(mContext).isOnline()) {
            if (result != null) {
                try {
                    PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);

                    if (result.getVersionCode() != null && result.getVersionCode() > pInfo.versionCode) {
                        this.versionDto = result;
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(versionDto.getDocumentationLink()));
                        PendingIntent resultPendingIntent = PendingIntent.getActivity(mContext,
                                0, intent, PendingIntent.FLAG_ONE_SHOT);
                        mBuilder = new NotificationCompat.Builder(
                                mContext).setSmallIcon(
                                R.drawable.ic_hentoid).setContentTitle(mContext.getString(R.string.new_version_available));
                        mBuilder.setContentText(mContext.getString(R.string.version_number).replace("@oldVersion", pInfo.versionName).replace("@newVersion", versionDto.getVersionName()));
                        mBuilder.setProgress(0, 0, false);
                        Notification notif = mBuilder.build();
                        notif.contentIntent = resultPendingIntent;
                        notif.flags = notif.flags | Notification.DEFAULT_LIGHTS
                                | Notification.FLAG_AUTO_CANCEL;

                        notificationManager.notify(0, notif);
                        Toast.makeText(mContext, R.string.find_new_version, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(mContext, R.string.not_find_new_version, Toast.LENGTH_SHORT).show();
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "update checker asynctask - onpost ", e);
                }
            } else {
                Toast.makeText(mContext, R.string.error_update_checker, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(mContext, "Not currently online.", Toast.LENGTH_SHORT).show();
        }
    }
}