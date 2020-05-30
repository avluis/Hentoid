package me.devsaki.hentoid.util;

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

public class TooltipUtil {

    private TooltipUtil() {
        throw new IllegalStateException("Utility class");
    }


    public static void showTooltip(
            @NonNull Context context,
            @StringRes int message,
            @NonNull ArrowOrientation orientation,
            @NonNull View anchor,
            @NonNull LifecycleOwner lifecycleOwner
    ) {
        Balloon balloon = new Balloon.Builder(context)
                .setArrowSize(10)
                .setArrowOrientation(orientation)
                .setArrowVisible(true)
                .setWidthRatio(1.0f)
                .setHeight(65)
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
                .setLifecycleOwner(lifecycleOwner)
                .setPreferenceName("tooltip." + getViewName(anchor))
                .build();

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
