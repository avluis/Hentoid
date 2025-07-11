package me.devsaki.hentoid.notification.appUpdate

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification

private const val INDETERMINATE = -1

class UpdateProgressNotification(private val progress: Int = INDETERMINATE) : BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.resources.getText(R.string.downloading_update))
            .setProgress(100, progress, progress == INDETERMINATE).setOnlyAlertOnce(true).build()
}
