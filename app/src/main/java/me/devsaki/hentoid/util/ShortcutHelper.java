package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;

import java.util.Arrays;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.websites.NhentaiActivity;

/**
 * Created by avluis on 11/04/2016.
 */

public final class ShortcutHelper {

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    public static void buildShortcuts(Context context) {
        // TODO: Loop across all activities
        int tint_color = ContextCompat.getColor(context, R.color.accent);
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);

        Bitmap nhentaiBitmap = Helper.getBitmapFromVectorDrawable(context, R.drawable.ic_menu_nhentai);
        nhentaiBitmap = Helper.tintBitmap(nhentaiBitmap, tint_color);
        Icon nhentaiIcon = Icon.createWithBitmap(nhentaiBitmap);

        Intent nhentaiIntent = new Intent(context, NhentaiActivity.class);
        nhentaiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        nhentaiIntent.setAction(Intent.ACTION_VIEW);

        ShortcutInfo nhentai = new ShortcutInfo.Builder(context, "nhentai")
                .setShortLabel("nhentai")
                .setLongLabel("Open nhentai")
                .setIcon(nhentaiIcon)
                .setIntent(nhentaiIntent)
                .build();

        shortcutManager.setDynamicShortcuts(Arrays.asList(nhentai));
    }
}
