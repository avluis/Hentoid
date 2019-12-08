package me.devsaki.hentoid.notification.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.FileHelper
import me.devsaki.hentoid.util.notification.Notification
import java.io.File

private val APK_MIMETYPE = MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk")

class UpdateInstallNotification(private val apkUri: Uri) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification {
        val intent: Intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val f = File(apkUri.path)
            val contentUri = FileProvider.getUriForFile(context, FileHelper.getFileProviderAuthority(), f)
            intent = Intent(Intent.ACTION_INSTALL_PACKAGE, contentUri)
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        } else {
            intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(apkUri, APK_MIMETYPE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        return NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_stat_hentoid)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVibrate(longArrayOf(1, 1, 1))
            .setContentTitle("Update ready")
            .setContentText("Tap to install")
            .setContentIntent(pendingIntent)
            .build()
    }
}
