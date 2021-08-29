package me.devsaki.hentoid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import me.devsaki.hentoid.util.AppHelper;
import me.devsaki.hentoid.workers.data.AppUpdateData;
import timber.log.Timber;

/**
 * Broadcast receiver for when an "update available" notification is tapped
 */
public class AppUpdateDownloadReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle input = intent.getExtras();
        if (input != null) {
            AppUpdateData.Parser data = new AppUpdateData.Parser(input);
            String apkUrl = data.getUrl();
            AppHelper.Companion.runUpdateDownloadWorker(context, apkUrl);
        } else Timber.w("no data");
    }
}
