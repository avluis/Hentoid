package me.devsaki.hentoid.util;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonAnimation;

import me.devsaki.hentoid.R;

public class TooltipHelper {

    private TooltipHelper() {
        throw new IllegalStateException("Utility class");
    }


    public static void showTooltip(
            @NonNull Context context,
            @StringRes int message,
            @NonNull ArrowOrientation orientation,
            @NonNull View anchor,
            @NonNull LifecycleOwner lifecycleOwner
    ) {
        showTooltip(context, message, orientation, anchor, lifecycleOwner, false);
    }

    public static void showTooltip(
            @NonNull Context context,
            @StringRes int message,
            @NonNull ArrowOrientation orientation,
            @NonNull View anchor,
            @NonNull LifecycleOwner lifecycleOwner,
            boolean always
    ) {
        String prefName = "tooltip." + getViewName(anchor);
        if (context instanceof Activity) prefName += "." + ((Activity) context).getLocalClassName();

        Balloon.Builder balloonBuilder = new Balloon.Builder(context)
                .setArrowSize(10)
                .setArrowOrientation(orientation)
                .setIsVisibleArrow(true)
                .setPadding(4)
                .setTextSize(15f)
                .setArrowPosition(0.5f)
                .setCornerRadius(4f)
                .setAlpha(0.9f)
                .setTextResource(message)
                .setTextColor(ContextCompat.getColor(context, R.color.white_opacity_87))
                .setIconDrawable(ContextCompat.getDrawable(context, R.drawable.ic_help_outline))
                .setBackgroundColor(ContextCompat.getColor(context, R.color.dark_gray))
                .setDismissWhenClicked(true)
                .setDismissWhenTouchOutside(true)
                .setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                .setLifecycleOwner(lifecycleOwner);

        if (!always) balloonBuilder.setPreferenceName(prefName);

        Balloon balloon = balloonBuilder.build();

        if (orientation.equals(ArrowOrientation.BOTTOM)) balloon.showAlignTop(anchor);
        else if (orientation.equals(ArrowOrientation.TOP)) balloon.showAlignBottom(anchor);
        else if (orientation.equals(ArrowOrientation.LEFT)) balloon.showAlignRight(anchor);
        else if (orientation.equals(ArrowOrientation.RIGHT)) balloon.showAlignLeft(anchor);
    }

    private static String getViewName(@NonNull final View view) {
        if (view.getId() == View.NO_ID) return "no-id";
        else return view.getResources().getResourceName(view.getId());
    }
}
