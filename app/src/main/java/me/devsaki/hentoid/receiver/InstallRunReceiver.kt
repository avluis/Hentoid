package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import me.devsaki.hentoid.util.ApkInstall
import me.devsaki.hentoid.util.FileHelper
import java.io.File

const val KEY_APK_PATH = "apk_path"

class InstallRunReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val apkPath = intent.getStringExtra(KEY_APK_PATH)
        if (apkPath.isNullOrEmpty()) return

        val contentUri = FileProvider.getUriForFile(context, FileHelper.getFileProviderAuthority(), File(apkPath))
        ApkInstall().install(context, contentUri)
    }
}

