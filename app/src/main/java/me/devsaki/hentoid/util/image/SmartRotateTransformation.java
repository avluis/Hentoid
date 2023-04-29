package me.devsaki.hentoid.util.image;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.security.MessageDigest;

public class SmartRotateTransformation extends BitmapTransformation {

    private final float rotateRotationAngle;
    private final int screenWidth;
    private final int screenHeight;

    public SmartRotateTransformation(float rotateRotationAngle, int screenWidth, int screenHeight) {
        this.rotateRotationAngle = rotateRotationAngle;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        Matrix matrix = new Matrix();

        if (ImageHelperK.INSTANCE.needsRotating(screenWidth, screenHeight, toTransform.getWidth(), toTransform.getHeight()))
            matrix.postRotate(rotateRotationAngle);

        return Bitmap.createBitmap(toTransform, 0, 0, toTransform.getWidth(), toTransform.getHeight(), matrix, true);
    }

    @Override
    public void updateDiskCacheKey(MessageDigest messageDigest) {
        messageDigest.update(("rotate" + rotateRotationAngle).getBytes());
    }
}