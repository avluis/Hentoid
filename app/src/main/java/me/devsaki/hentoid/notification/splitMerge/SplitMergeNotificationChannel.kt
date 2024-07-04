package me.devsaki.hentoid.notification.splitMerge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import me.devsaki.hentoid.R
import java.util.Objects

internal const val ID = "splitMerge"

// IMPORTANT : ALWAYS INIT THE CHANNEL BEFORE FIRING NOTIFICATIONS !
fun initChannelSplitMerge(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = context.getString(R.string.notif_split_merge)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(ID, name, importance)
        channel.setSound(null, null)
        channel.vibrationPattern = null

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        Objects.requireNonNull(notificationManager, "notificationManager must not be null")
        notificationManager.createNotificationChannel(channel)
    }
}