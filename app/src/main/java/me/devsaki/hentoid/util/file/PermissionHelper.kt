package me.devsaki.hentoid.util.file

import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

const val RQST_STORAGE_PERMISSION = 3
const val RQST_NOTIFICATION_PERMISSION = 4

private fun Context.checkPermission(code: String): Boolean {
    return ContextCompat.checkSelfPermission(this, code) == PERMISSION_GRANTED
}

fun Activity.requestExternalStorageReadPermission(permissionRequestCode: Int): Boolean {
    return if (checkPermission(READ_EXTERNAL_STORAGE)) true
    else {
        ActivityCompat.requestPermissions(
            this, arrayOf(READ_EXTERNAL_STORAGE),
            permissionRequestCode
        )
        false
    }
}

fun Activity.checkExternalStorageReadWritePermission(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return true
    return checkPermission(READ_EXTERNAL_STORAGE) &&
            checkPermission(WRITE_EXTERNAL_STORAGE)
}

fun Activity.requestExternalStorageReadWritePermission(permissionRequestCode: Int): Boolean {
    return if (checkExternalStorageReadWritePermission() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) true else {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                READ_EXTERNAL_STORAGE,
                WRITE_EXTERNAL_STORAGE
            ), permissionRequestCode
        )
        false
    }
}

fun Context.checkNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        checkPermission(POST_NOTIFICATIONS)
    } else true
}

fun Activity.requestNotificationPermission(permissionRequestCode: Int): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (checkNotificationPermission()) true else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(POST_NOTIFICATIONS),
                permissionRequestCode
            )
            false
        }
    } else true
}