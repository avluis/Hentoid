package me.devsaki.hentoid.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import androidx.core.app.ActivityCompat;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class PermissionUtil {

    public static boolean requestExternalStoragePermission(Activity activity, int permissionRequestCode) {
        if (ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {

            return true;
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE}, permissionRequestCode);

            return false;
        }
    }

    public static boolean checkExternalStoragePermission(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED;
    }
}
