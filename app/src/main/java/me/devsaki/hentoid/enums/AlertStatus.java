package me.devsaki.hentoid.enums;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import me.devsaki.hentoid.R;

/**
 * Site issues alert levels
 */
public enum AlertStatus {

    ORANGE(R.color.orange, R.drawable.ic_exclamation),
    RED(R.color.red, R.drawable.ic_error),
    GREY(R.color.dark_gray, R.drawable.ic_warning),
    BLACK(R.color.black, R.drawable.ic_nuclear),
    NONE(R.color.white, R.drawable.ic_info);

    private final int color;
    private final int icon;

    AlertStatus(@ColorRes int color, @DrawableRes int icon) {
        this.color = color;
        this.icon = icon;
    }

    public @ColorRes
    int getColor() {
        return color;
    }

    public @ColorRes
    int getIcon() {
        return icon;
    }


    // Same as ValueOf with a fallback to NONE
    // (vital for forward compatibility)
    public static AlertStatus searchByName(String name) {
        for (AlertStatus s : values())
            if (s.name().equalsIgnoreCase(name)) return s;

        return NONE;
    }
}
