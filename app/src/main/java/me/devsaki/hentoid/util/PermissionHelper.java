package me.devsaki.hentoid.util;

import android.Manifest;
import android.app.Activity;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class PermissionHelper {

    public static final int RQST_STORAGE_PERMISSION = 3;


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
        return (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED);
    }

    public static boolean requestExternalStorageReadWritePermission(Activity activity, int permissionRequestCode) {
        if (checkExternalStorageReadWritePermission(activity))
            return true;
        else {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, permissionRequestCode);

            return false;
        }
    }
}
