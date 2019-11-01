package me.devsaki.hentoid.enums;

import androidx.annotation.ColorRes;

import me.devsaki.hentoid.R;

/**
 * Created by Robb on 2019/11
 */
public enum AlertStatus {

    ORANGE(R.color.orange),
    RED(R.color.red),
    BLACK(R.color.black),
    NONE(R.color.white);

    private final @ColorRes
    int color;

    AlertStatus(@ColorRes int color) {
        this.color = color;
    }

    public @ColorRes
    int getColor() {
        return color;
    }
}
