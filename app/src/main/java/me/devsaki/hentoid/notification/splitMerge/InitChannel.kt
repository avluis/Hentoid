package me.devsaki.hentoid.notification.splitMerge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import me.devsaki.hentoid.R

internal const val ID = "splitMerge"

// IMPORTANT : ALWAYS INIT THE CHANNEL BEFORE FIRING NOTIFICATIONS !
fun init(context: Context) {
    val name = context.getString(R.string.notif_split_merge)
    val importance = NotificationManager.IMPORTANCE_DEFAULT
    val channel = NotificationChannel(ID, name, importance)
    channel.setSound(null, null)
    channel.vibrationPattern = null

    context.getSystemService<NotificationManager?>(NotificationManager::class.java)?.apply {
        createNotificationChannel(channel)
    }
}