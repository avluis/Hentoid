package me.devsaki.hentoid.customssiv;

import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.ScriptIntrinsicResize;
import android.renderscript.Type;

import androidx.annotation.NonNull;

import timber.log.Timber;

// Credits go to https://medium.com/@petrakeas/alias-free-resize-with-renderscript-5bf15a86ce3
class ResizeBitmapHelper {

    private ResizeBitmapHelper() {
        throw new IllegalStateException("Utility class");
    }

    /*
    static Bitmap successiveResize(@NonNull final Bitmap src, int resizeNum) {
        if (0 == resizeNum) return src;

        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        Bitmap output = src;
        for (int i = 0; i < resizeNum; i++) {
            srcWidth /= 2;
            srcHeight /= 2;
            Bitmap temp = Bitmap.createScaledBitmap(output, srcWidth, srcHeight, true);
            if (i != 0) { // don't recycle the src bitmap
                output.recycle();
            }
            output = temp;
        }
        return output;
    }

    // RENDERSCRIPT ALTERNATE IMPLEMENTATIONS (requires API 21+)

    // Direct equivalent to the 1st method, using RenderScript
    static Bitmap successiveResize(@NonNull final RenderScript rs, @NonNull final Bitmap src, int resizeNum) {
        if (resizeNum < 1) return src;

        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        Bitmap.Config config = src.getConfig();

        Allocation srcAllocation = Allocation.createFromBitmap(rs, src);
        ScriptIntrinsicResize resizeScript = ScriptIntrinsicResize.create(rs);

        Allocation outAllocation = null;
        for (int i = 0; i < resizeNum; i++) {
            srcWidth /= 2;
            srcHeight /= 2;

            Type t = Type.createXY(rs, srcAllocation.getElement(), srcWidth, srcHeight);
            outAllocation = Allocation.createTyped(rs, t);
            resizeScript.setInput(srcAllocation);
            resizeScript.forEach_bicubic(outAllocation);

            srcAllocation.destroy();
            srcAllocation = outAllocation;
        }

        Bitmap output = Bitmap.createBitmap(srcWidth, srcHeight, config);
        outAllocation.copyTo(output);

        resizeScript.destroy();
        outAllocation.destroy();

        return output;
    }

     */

    // Better-looking resizing using RenderScript entirely, in one pass
    // Apply Gaussian blur to the image and then subsample it using bicubic interpolation.
    static Bitmap resizeNice(@NonNull final RenderScript rs, final Bitmap src, float xScale, float yScale) {
        // Calculate gaussian's radius
        float sigma = (1 / xScale) / (float) Math.PI;
        // https://android.googlesource.com/platform/frameworks/rs/+/master/cpu_ref/rsCpuIntrinsicBlur.cpp
        float radius = 2.5f * sigma/* - 1.5f*/; // Works better that way
        radius = Math.min(25, Math.max(0.0001f, radius));
        Timber.d(">> using sigma=%s for xScale=%s => radius=%s", sigma, xScale, radius);

        // Defensive programming in case the threading/view recycling recycles a bitmap just before that methods is reached
        if (null == src || src.isRecycled()) return src;

        Bitmap.Config bitmapConfig = src.getConfig();
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        int dstWidth = Math.round(srcWidth * xScale);
        int dstHeight = Math.round(srcHeight * yScale);
        src.setHasAlpha(false);

        // Gaussian filter
        Allocation tmpIn = Allocation.createFromBitmap(rs, src);
        Allocation tmpFiltered = Allocation.createTyped(rs, tmpIn.getType());
        ScriptIntrinsicBlur blurInstrinsic = ScriptIntrinsicBlur.create(rs, tmpIn.getElement());

        blurInstrinsic.setRadius(radius);
        blurInstrinsic.setInput(tmpIn);
        blurInstrinsic.forEach(tmpFiltered);

        src.recycle();
        tmpIn.destroy();
        blurInstrinsic.destroy();


        // Resize
        Bitmap dst = Bitmap.createBitmap(dstWidth, dstHeight, bitmapConfig);
        Type t = Type.createXY(rs, tmpFiltered.getElement(), dstWidth, dstHeight);
        Allocation tmpOut = Allocation.createTyped(rs, t);
        ScriptIntrinsicResize resizeIntrinsic = ScriptIntrinsicResize.create(rs);

        resizeIntrinsic.setInput(tmpFiltered);
        resizeIntrinsic.forEach_bicubic(tmpOut);
        tmpOut.copyTo(dst);

        tmpFiltered.destroy();
        resizeIntrinsic.destroy();
        tmpOut.destroy();
/*
        // Additional sharpen script just in case (WIP)
        Allocation tmpSharpOut = Allocation.createTyped(rs, t);
        //ScriptIntrinsicConvolve3x3 sharpen = ScriptIntrinsicConvolve3x3.create(rs, tmpOut.getElement());
        ScriptIntrinsicConvolve3x3 sharpen = ScriptIntrinsicConvolve3x3.create(rs, Element.U8_4(rs));
        sharpen.setCoefficients(getSharpenCoefficients());
        sharpen.setInput(tmpOut);
        sharpen.forEach(tmpSharpOut);

        tmpSharpOut.copyTo(dst);

        tmpOut.destroy();
        tmpSharpOut.destroy();
        sharpen.destroy();
*/

        return dst;
    }
/*
    private static float[] getSharpenCoefficients() {
        return new float[]{
                0f, -1f, 0f,
                -1f, -5f, -1f,
                0f, -1f, 0f
        };
    }
*/
}
