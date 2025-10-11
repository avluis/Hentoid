package me.devsaki.hentoid.notification.delete

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.BaseDeleteWorker
import java.util.Locale

class DeleteProgressNotification(
    private val title: String,
    private val progress: Int,
    private val max: Int,
    private val operation: BaseDeleteWorker.Operation,
    private val target: BaseDeleteWorker.Target,
    private val isCleaning : Boolean = false
) : BaseNotification() {

    private val progressString: String = " %.2f%%".format(Locale.US, progress * 100.0 / max)

    override fun onCreateNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(
                context.getString(
                    when (operation) {
                        BaseDeleteWorker.Operation.PURGE -> R.string.purge_progress
                        BaseDeleteWorker.Operation.STREAM -> R.string.stream_progress
                        else -> when (target) { // Delete... something
                            BaseDeleteWorker.Target.BOOK -> R.string.delete_books_progress
                            BaseDeleteWorker.Target.CHAPTER -> R.string.delete_chapters_progress
                            BaseDeleteWorker.Target.IMAGE -> R.string.delete_images_progress
                            else -> if (isCleaning) R.string.clean_items_progress else R.string.delete_items_progress
                        }
                    }
                )
            )
            .setContentText(title)
            .setContentInfo(progressString)
            .setProgress(max, progress, false)
            .setColor(context.getThemedColor(R.color.secondary_light))
            .setLocalOnly(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
