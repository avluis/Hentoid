package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.activities.UnlockActivity;
import me.devsaki.hentoid.enums.Site;

/**
 * Created by avluis on 11/04/2016.
 */

@RequiresApi(api = Build.VERSION_CODES.N_MR1)
public final class ShortcutHelper {

    private ShortcutHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static void buildShortcuts(Context context) {
        // TODO: Loop across all activities
        List<ShortcutInfo> shortcuts = new ArrayList<>();
        shortcuts.add(buildShortcut(context, Site.NHENTAI));

        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager != null)
            shortcutManager.setDynamicShortcuts(shortcuts);
    }

    private static ShortcutInfo buildShortcut(Context context, Site s) {
        int tint_color = ThemeHelper.getColor(context, "secondary");
        Bitmap siteBitmap = Helper.getBitmapFromVectorDrawable(context, s.getIco());
        siteBitmap = Helper.tintBitmap(siteBitmap, tint_color);
        Icon siteIcon = Icon.createWithBitmap(siteBitmap);

        Intent siteIntent = UnlockActivity.wrapIntent(context, s);

        return new ShortcutInfo.Builder(context, s.getDescription().toLowerCase())
                .setShortLabel(s.getDescription().toLowerCase())
                .setLongLabel("Open " + s.getDescription().toLowerCase())
                .setIcon(siteIcon)
                .setIntent(siteIntent)
                .build();
    }
}
