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

object PermissionHelper {
    const val RQST_STORAGE_PERMISSION = 3
    const val RQST_NOTIFICATION_PERMISSION = 4


    private fun checkPermission(context: Context, code: String): Boolean {
        return ContextCompat.checkSelfPermission(context, code) == PERMISSION_GRANTED
    }

    fun requestExternalStorageReadPermission(
        activity: Activity,
        permissionRequestCode: Int
    ): Boolean {
        return if (checkPermission(activity, READ_EXTERNAL_STORAGE)) true
        else {
            ActivityCompat.requestPermissions(
                activity, arrayOf(READ_EXTERNAL_STORAGE),
                permissionRequestCode
            )
            false
        }
    }

    fun checkExternalStorageReadWritePermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return true
        return checkPermission(activity, READ_EXTERNAL_STORAGE) &&
                checkPermission(activity, WRITE_EXTERNAL_STORAGE)
    }

    fun requestExternalStorageReadWritePermission(
        activity: Activity,
        permissionRequestCode: Int
    ): Boolean {
        return if (checkExternalStorageReadWritePermission(activity) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) true else {
            ActivityCompat.requestPermissions(
                activity, arrayOf(
                    READ_EXTERNAL_STORAGE,
                    WRITE_EXTERNAL_STORAGE
                ), permissionRequestCode
            )
            false
        }
    }

    fun checkNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermission(context, POST_NOTIFICATIONS)
        } else true
    }

    fun requestNotificationPermission(activity: Activity, permissionRequestCode: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkNotificationPermission(activity)) true else {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(POST_NOTIFICATIONS),
                    permissionRequestCode
                )
                false
            }
        } else true
    }
}