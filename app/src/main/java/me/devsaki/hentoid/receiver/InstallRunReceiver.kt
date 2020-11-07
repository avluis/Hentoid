/*
  Copyright (c) 2019 CommonsWare, LLC

  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain	a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
  by applicable law or agreed to in writing, software distributed under the
  License is distributed on an "AS IS" BASIS,	WITHOUT	WARRANTIES OR CONDITIONS
  OF ANY KIND, either express or implied. See the License for the specific
  language governing permissions and limitations under the License.

  Covered in detail in the book _Elements of Android Q

  https://commonsware.com/AndroidQ
*/

package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import me.devsaki.hentoid.util.ApkInstall
import me.devsaki.hentoid.util.FileHelper
import java.io.File

private const val TAG = "AppInstaller"
const val KEY_APK_PATH = "apk_path"

class InstallRunReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val apkPath = intent.getStringExtra(KEY_APK_PATH)
        if (apkPath.isNullOrEmpty()) return

        val contentUri = FileProvider.getUriForFile(context, FileHelper.getFileProviderAuthority(), File(apkPath))
        ApkInstall().install(context, contentUri)
    }
}

