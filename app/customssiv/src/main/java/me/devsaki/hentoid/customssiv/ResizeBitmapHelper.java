package me.devsaki.hentoid.customssiv;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.ScriptIntrinsicResize;
import android.renderscript.Type;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.customssiv.util.Helper;
import me.devsaki.hentoid.gpu_render.GPUImage;
import me.devsaki.hentoid.gpu_render.filter.GPUImageFilter;
import me.devsaki.hentoid.gpu_render.filter.GPUImageGaussianBlurFilter;
import me.devsaki.hentoid.gpu_render.filter.GPUImageResizeFilter;
import timber.log.Timber;

// Credits go to https://medium.com/@petrakeas/alias-free-resize-with-renderscript-5bf15a86ce3
class ResizeBitmapHelper {

    private ResizeBitmapHelper() {
        throw new IllegalStateException("Utility class");
    }

    private static GPUImage gpuImage = null;


    static ImmutablePair<Bitmap, Float> resizeBitmap(final RenderScript rs, @NonNull final Bitmap src, float targetScale) {
        Helper.assertNonUiThread();
        if (null == rs) {
            ImmutablePair<Integer, Float> resizeParams = computeResizeParams(targetScale);
            Timber.d(">> resizing successively to scale %s", resizeParams.right);
            return new ImmutablePair<>(successiveResize(src, resizeParams.left), resizeParams.right);
        } else {
            if (targetScale < 0.75 || (targetScale > 1.0 && targetScale < 1.55)) {
                // Don't use resize nice above 0.75%; classic bilinear resize does the job well with more sharpness to the picture
                /*
                return new ImmutablePair<>(resizeRenderScript(rs, src, targetScale, targetScale), targetScale);
                 */
                return new ImmutablePair<>(resizeGLES(rs.getApplicationContext(), src, targetScale, targetScale), targetScale);
            } else {
                Timber.d(">> No resize needed; keeping raw image");
                return new ImmutablePair<>(src, 1f);
            }
        }
    }

    /**
     * Compute resizing parameters according to the given target scale
     * TODO can that algorithm be merged with CustomSubsamplingScaleImageView.calculateInSampleSize ?
     *
     * @param targetScale target scale of the image to display (% of the raw dimensions)
     * @return Pair containing
     * - First : Number of half-resizes to perform (see {@link ResizeBitmapHelper})
     * - Second : Corresponding scale
     */
    private static ImmutablePair<Integer, Float> computeResizeParams(final float targetScale) {
        float resultScale = 1f;
        int nbResize = 0;

        // Resize when approaching the target scale by 1/3 because there may already be artifacts displayed at that point
        // (seen with full-res pictures resized to 65% with Android's default bilinear filtering)
        for (int i = 1; i < 10; i++) if (targetScale < Math.pow(0.5, i) * 1.33) nbResize++;
        if (nbResize > 0) resultScale = (float) Math.pow(0.5, nbResize);

        return new ImmutablePair<>(nbResize, resultScale);
    }

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

    static Bitmap resizeGLES(final Context context, final Bitmap src, float xScale, float yScale) {
        if (null == gpuImage) gpuImage = new GPUImage(context); // TODO change that

        // Calculate gaussian's radius
        float sigma = (1 / xScale) / (float) Math.PI;
        // https://android.googlesource.com/platform/frameworks/rs/+/master/cpu_ref/rsCpuIntrinsicBlur.cpp
        float radius = 2.5f * sigma/* - 1.5f*/; // Works better that way
        radius = Math.min(25, Math.max(0.0001f, radius));
        Timber.d(">> using sigma=%s for xScale=%s => radius=%s", sigma, xScale, radius);

        // Defensive programming in case the threading/view recycling recycles a bitmap just before that methods is reached
        if (null == src || src.isRecycled()) return src;

        Timber.d(">> bmp IN %dx%d", src.getWidth(), src.getHeight());

        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        // Must be multiple of 2
        int dstWidth = Math.round(srcWidth * xScale);
        if (1 == dstWidth % 2) dstWidth += 1;
        int dstHeight = Math.round(srcHeight * yScale);
        if (1 == dstHeight % 2) dstHeight += 1;
        src.setHasAlpha(false);

        List<GPUImageFilter> filterList = new ArrayList<>();
        filterList.add(new GPUImageGaussianBlurFilter(radius));
        filterList.add(new GPUImageResizeFilter(dstWidth, dstHeight));

        Bitmap out = gpuImage.getBitmapForMultipleFilters(filterList, src);
        Timber.d(">> bmp OUT %dx%d => %dx%d %d", src.getWidth(), src.getHeight(), out.getWidth(), out.getHeight(), out.getAllocationByteCount());
        return out;
    }

    // Better-looking resizing using RenderScript entirely, in one pass
    // Apply Gaussian blur to the image and then subsample it using bicubic interpolation.
    static Bitmap resizeRenderScript(@NonNull final RenderScript rs, final Bitmap src, float xScale, float yScale) {
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

        return dst;
    }
}
