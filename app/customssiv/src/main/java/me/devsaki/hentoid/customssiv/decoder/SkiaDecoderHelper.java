package me.devsaki.hentoid.customssiv.decoder;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.List;

public class SkiaDecoderHelper {

    private SkiaDecoderHelper() {
        throw new IllegalStateException("Utility class");
    }


    public static int getResourceId(@NonNull final Context context, @NonNull final Uri uri) throws PackageManager.NameNotFoundException {
        Resources res;
        String packageName = uri.getAuthority();
        if (context.getPackageName().equals(packageName)) {
            res = context.getResources();
        } else {
            PackageManager pm = context.getPackageManager();
            res = pm.getResourcesForApplication(packageName);
        }

        int result = 0;
        List<String> segments = uri.getPathSegments();
        int size = segments.size();
        if (size == 2 && segments.get(0).equals("drawable")) {
            String resName = segments.get(1);
            result = res.getIdentifier(resName, "drawable", packageName);
        } else if (size == 1 && TextUtils.isDigitsOnly(segments.get(0))) {
            try {
                result = Integer.parseInt(segments.get(0));
            } catch (NumberFormatException ignored) {
                // Ignored exception
            }
        }

        return result;
    }

}
