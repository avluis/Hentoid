package me.devsaki.hentoid.notification.appUpdate

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.APK_MIMETYPE

class UpdateInstallNotification(private val apkUri: Uri) : BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVibrate(longArrayOf(1, 1, 1))
            .setContentTitle(context.resources.getText(R.string.update_ready))
            .setContentText(context.resources.getText(R.string.tap_to_install))
            .setContentIntent(getIntent(context))
            .build()

    private fun getIntent(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(apkUri, APK_MIMETYPE)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        val flags =
            if (Build.VERSION.SDK_INT > 30)
                PendingIntent.FLAG_IMMUTABLE
            else 0
        return PendingIntent.getActivity(context, 0, intent, flags)
    }
}
