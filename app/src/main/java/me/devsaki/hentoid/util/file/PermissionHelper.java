package me.devsaki.hentoid.util.file;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.app.Activity;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionHelper {

    public static final int RQST_STORAGE_PERMISSION = 3;
    public static final int RQST_NOTIFICATION_PERMISSION = 4;


    private PermissionHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean requestExternalStorageReadPermission(Activity activity, int permissionRequestCode) {
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
            return true;
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE}, permissionRequestCode);
            return false;
        }
    }

    public static boolean checkExternalStorageReadWritePermission(Activity activity) {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                || (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED)
        );
    }

    public static boolean requestExternalStorageReadWritePermission(Activity activity, int permissionRequestCode) {
        if (checkExternalStorageReadWritePermission(activity) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            return true;
        else {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, permissionRequestCode);
            return false;
        }
    }

    public static boolean checkNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PERMISSION_GRANTED;
        } else return true;
    }

    public static boolean requestNotificationPermission(Activity activity, int permissionRequestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkNotificationPermission(activity)) return true;
            else {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, permissionRequestCode);
                return false;
            }
        } else return true;
    }
}