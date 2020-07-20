package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.UnlockActivity;
import me.devsaki.hentoid.enums.Site;

import static android.graphics.Bitmap.Config.ARGB_8888;

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
        int tintColor = ThemeHelper.getColor(context, R.color.secondary_light);
        Bitmap siteBitmap = getBitmapFromVectorDrawable(context, s.getIco());
        siteBitmap = tintBitmap(siteBitmap, tintColor);
        Icon siteIcon = Icon.createWithBitmap(siteBitmap);

        Intent siteIntent = UnlockActivity.wrapIntent(context, s);

        return new ShortcutInfo.Builder(context, s.getDescription().toLowerCase())
                .setShortLabel(s.getDescription().toLowerCase())
                .setLongLabel("Open " + s.getDescription().toLowerCase())
                .setIcon(siteIcon)
                .setIntent(siteIntent)
                .build();
    }

    private static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable d = ContextCompat.getDrawable(context, drawableId);

        if (d != null) {
            Bitmap b = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, c.getWidth(), c.getHeight());
            d.draw(c);

            return b;
        } else {
            return Bitmap.createBitmap(0, 0, Bitmap.Config.ARGB_8888);
        }
    }

    private static Bitmap tintBitmap(Bitmap bitmap, int color) {
        Paint p = new Paint();
        p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        Bitmap b = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawBitmap(bitmap, 0, 0, p);

        return b;
    }
}
