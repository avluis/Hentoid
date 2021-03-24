package me.devsaki.hentoid.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.skydoves.powermenu.MenuAnimation;
import com.skydoves.powermenu.OnMenuItemClickListener;
import com.skydoves.powermenu.PowerMenu;
import com.skydoves.powermenu.PowerMenuItem;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Helper;

public class AddQueueMenu {

    private AddQueueMenu() {
        throw new IllegalStateException("Utility class");
    }


    public static void show(
            @NonNull final Context context,
            @NonNull final View anchor,
            @NonNull final LifecycleOwner lifecycle,
            @NonNull final OnMenuItemClickListener<PowerMenuItem> listener) {
        Resources res = context.getResources();
        PowerMenu powerMenu = new PowerMenu.Builder(context)
                .addItem(new PowerMenuItem(res.getString(R.string.queue_add_top), R.drawable.ic_action_top, false))
                .addItem(new PowerMenuItem(res.getString(R.string.queue_add_bottom), R.drawable.ic_action_bottom, false))
                .setAnimation(MenuAnimation.SHOWUP_TOP_LEFT)
                .setMenuRadius(10f)
                .setLifecycleOwner(lifecycle)
                .setTextColor(ContextCompat.getColor(context, R.color.white_opacity_87))
                .setTextTypeface(Typeface.DEFAULT)
                .setMenuColor(ContextCompat.getColor(context, R.color.dark_gray))
                .setTextSize(Helper.dimensAsDp(context, R.dimen.text_subtitle_1))
                .setAutoDismiss(true)
                .build();

        powerMenu.setOnMenuItemClickListener(listener);

        powerMenu.setIconColor(ContextCompat.getColor(context, R.color.white_opacity_87));
        powerMenu.showAtCenter(anchor);
    }
}
