package me.devsaki.hentoid.util;

import static android.graphics.Bitmap.Config.ARGB_8888;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.waynejo.androidndkgif.GifEncoder;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import timber.log.Timber;

/**
 * Generic utility class
 */
public final class ImageHelper {

    private static final Charset CHARSET_LATIN_1 = StandardCharsets.ISO_8859_1;

    public static final String MIME_IMAGE_GENERIC = "image/*";
    public static final String MIME_IMAGE_WEBP = "image/webp";
    public static final String MIME_IMAGE_JPEG = "image/jpeg";
    public static final String MIME_IMAGE_GIF = "image/gif";
    public static final String MIME_IMAGE_PNG = "image/png";
    public static final String MIME_IMAGE_APNG = "image/apng";


    private static FileHelper.NameFilter imageNamesFilter;


    private ImageHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Determine if the given image file extension is supported by the app
     *
     * @param extension File extension to test
     * @return True if the app supports the reading of images with the given file extension; false if not
     */
    public static boolean isImageExtensionSupported(String extension) {
        return extension.equalsIgnoreCase("jpg")
                || extension.equalsIgnoreCase("jpeg")
                || extension.equalsIgnoreCase("jfif")
                || extension.equalsIgnoreCase("gif")
                || extension.equalsIgnoreCase("png")
                || extension.equalsIgnoreCase("webp");
    }

    public static boolean isSupportedImage(@NonNull final String fileName) {
        return isImageExtensionSupported(FileHelper.getExtension(fileName));
    }

    /**
     * Build a {@link FileHelper.NameFilter} only accepting image files supported by the app
     *
     * @return {@link FileHelper.NameFilter} only accepting image files supported by the app
     */
    public static FileHelper.NameFilter getImageNamesFilter() {
        if (null == imageNamesFilter)
            imageNamesFilter = displayName -> ImageHelper.isImageExtensionSupported(FileHelper.getExtension(displayName));
        return imageNamesFilter;
    }

    /**
     * Determine the MIME-type of the given binary data if it's a picture
     *
     * @param binary Picture binary data to determine the MIME-type for
     * @return MIME-type of the given binary data; empty string if not supported
     */
    public static String getMimeTypeFromPictureBinary(byte[] binary) {
        if (binary.length < 12) return "";

        // In Java, byte type is signed !
        // => Converting all raw values to byte to be sure they are evaluated as expected
        if ((byte) 0xFF == binary[0] && (byte) 0xD8 == binary[1] && (byte) 0xFF == binary[2])
            return MIME_IMAGE_JPEG;
        else if ((byte) 0x89 == binary[0] && (byte) 0x50 == binary[1] && (byte) 0x4E == binary[2]) {
            // Detect animated PNG : To be recognized as APNG an 'acTL' chunk must appear in the stream before any 'IDAT' chunks
            int acTlPos = FileHelper.findSequencePosition(binary, 0, "acTL".getBytes(CHARSET_LATIN_1), (int) (binary.length * 0.2));
            if (acTlPos > -1) {
                long idatPos = FileHelper.findSequencePosition(binary, acTlPos, "IDAT".getBytes(CHARSET_LATIN_1), (int) (binary.length * 0.1));
                if (idatPos > -1) return MIME_IMAGE_APNG;
            }
            return MIME_IMAGE_PNG;
        } else if ((byte) 0x47 == binary[0] && (byte) 0x49 == binary[1] && (byte) 0x46 == binary[2])
            return MIME_IMAGE_GIF;
        else if ((byte) 0x52 == binary[0] && (byte) 0x49 == binary[1] && (byte) 0x46 == binary[2] && (byte) 0x46 == binary[3]
                && (byte) 0x57 == binary[8] && (byte) 0x45 == binary[9] && (byte) 0x42 == binary[10] && (byte) 0x50 == binary[11])
            return MIME_IMAGE_WEBP;
        else if ((byte) 0x42 == binary[0] && (byte) 0x4D == binary[1]) return "image/bmp";
        else return MIME_IMAGE_GENERIC;
    }

    /**
     * Analyze the given binary picture header to try and detect if the picture is animated.
     * If the format is supported by the app, returns true if animated (animated GIF, APNG, animated WEBP); false if not
     *
     * @param data Binary picture file header (400 bytes minimum)
     * @return True if the format is animated and supported by the app
     */
    boolean isImageAnimated(byte[] data) {
        if (data.length < 400) return false;

        switch (getMimeTypeFromPictureBinary(data)) {
            case MIME_IMAGE_APNG:
                return true;
            case MIME_IMAGE_GIF:
                return FileHelper.findSequencePosition(data, 0, "NETSCAPE".getBytes(CHARSET_LATIN_1), 400) > -1;
            case MIME_IMAGE_WEBP:
                return FileHelper.findSequencePosition(data, 0, "ANIM".getBytes(CHARSET_LATIN_1), 400) > -1;
            default:
                return false;
        }
    }

    /**
     * Try to detect the mime-type of the picture file at the given URI
     *
     * @param context Context to use
     * @param uri     URI of the picture file to detect the mime-type for
     * @return Mime-type of the picture file at the given URI; MIME_IMAGE_GENERIC if no Mime-type detected
     */
    public static String getMimeTypeFromUri(@NonNull Context context, @NonNull Uri uri) {
        String result = MIME_IMAGE_GENERIC;
        byte[] buffer = new byte[12];
        try (InputStream is = FileHelper.getInputStream(context, uri)) {
            if (buffer.length == is.read(buffer))
                result = getMimeTypeFromPictureBinary(buffer);
        } catch (IOException e) {
            Timber.w(e);
        }
        return result;
    }

    /**
     * Convert the given Drawable ID into a Bitmap
     *
     * @param context    Context to be used
     * @param drawableId Drawable ID to get the Bitmap from
     * @return Given drawable ID rendered into a Bitmap
     */
    public static Bitmap getBitmapFromVectorDrawable(@NonNull final Context context, @DrawableRes int drawableId) {
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

    public static byte[] BitmapToWebp(@NonNull Bitmap bitmap) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output);
        return output.toByteArray();
    }

    /**
     * Tint the given Bitmap with the given color
     *
     * @param bitmap Bitmap to be tinted
     * @param color  Color to use as tint
     * @return Given Bitmap tinted with the given color
     */
    public static Bitmap tintBitmap(Bitmap bitmap, @ColorInt int color) {
        Paint p = new Paint();
        p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        Bitmap b = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawBitmap(bitmap, 0, 0, p);

        return b;
    }

    /**
     * Calculate sample size to load the picture with, according to raw and required dimensions
     *
     * @param rawWidth     Raw width of the picture, in pixels
     * @param rawHeight    Raw height of the picture, in pixels
     * @param targetWidth  Target width of the picture, in pixels
     * @param targetHeight Target height of the picture, in pixels
     * @return Sample size to use to load the picture with
     */
    public static int calculateInSampleSize(int rawWidth, int rawHeight, int targetWidth, int targetHeight) {
        // Raw height and width of image
        int inSampleSize = 1;

        if (rawHeight > targetHeight || rawWidth > targetWidth) {

            final int halfHeight = rawHeight / 2;
            final int halfWidth = rawWidth / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= targetHeight
                    && (halfWidth / inSampleSize) >= targetWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * Create a Bitmap from the given InputStream, optimizing resources according to the given required width and height
     *
     * @param stream       Stream to load the bitmap from
     * @param targetWidth  Target picture width, in pixels
     * @param targetHeight Target picture height, in pixels
     * @return Bitmap created from the given InputStream
     * @throws IOException If anything bad happens at load-time
     */
    public static Bitmap decodeSampledBitmapFromStream(@NonNull InputStream stream, int targetWidth, int targetHeight) throws IOException {
        List<InputStream> streams = Helper.duplicateInputStream(stream, 2);
        InputStream workStream1 = streams.get(0);
        InputStream workStream2 = streams.get(1);

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(workStream1, null, options);
        if (null == workStream2) {
            workStream1.reset();
            workStream2 = workStream1;
        } else {
            workStream1.close();
        }

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, targetWidth, targetHeight);

        // Decode final bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        try {
            return BitmapFactory.decodeStream(workStream2, null, options);
        } finally {
            workStream2.close();
        }
    }

    public static Uri assembleGif(
            @NonNull Context context,
            @NonNull File folder, // GIF encoder only work with paths...
            @NonNull List<ImmutablePair<Uri, Integer>> frames) throws IOException, IllegalArgumentException {
        if (frames.isEmpty()) throw new IllegalArgumentException("No frames given");

        int width;
        int height;
        try (InputStream is = FileHelper.getInputStream(context, frames.get(0).left)) {
            Bitmap b = BitmapFactory.decodeStream(is);
            width = b.getWidth();
            height = b.getHeight();
        }

        String path = new File(folder, "tmp.gif").getAbsolutePath();
        GifEncoder gifEncoder = new GifEncoder();
        try {
            gifEncoder.init(width, height, path, GifEncoder.EncodingType.ENCODING_TYPE_NORMAL_LOW_MEMORY);

            for (ImmutablePair<Uri, Integer> frame : frames) {
                try (InputStream is = FileHelper.getInputStream(context, frame.left)) {
                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                    Bitmap b = BitmapFactory.decodeStream(is, null, options);
                    if (null == b) continue;

                    try {
                        gifEncoder.encodeFrame(b, 100);
                    } finally {
                        b.recycle();
                    }
                }
            }
        } finally {
            gifEncoder.close();
        }

        return Uri.fromFile(new File(path));
    }

    /**
     * @param bitmap                the Bitmap to be scaled
     * @param threshold             the maxium dimension (either width or height) of the scaled bitmap
     * @param isNecessaryToKeepOrig is it necessary to keep the original bitmap? If not recycle the original bitmap to prevent memory leak.
     */
    public static Bitmap getScaledDownBitmap(@NonNull Bitmap bitmap, int threshold, boolean isNecessaryToKeepOrig) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int newWidth = width;
        int newHeight = height;

        if (width > height && width > threshold) {
            newWidth = threshold;
            newHeight = (int) (height * (float) newWidth / width);
        }

        if (width > height && width <= threshold) {
            //the bitmap is already smaller than our required dimension, no need to resize it
            return bitmap;
        }

        if (width < height && height > threshold) {
            newHeight = threshold;
            newWidth = (int) (width * (float) newHeight / height);
        }

        if (width < height && height <= threshold) {
            //the bitmap is already smaller than our required dimension, no need to resize it
            return bitmap;
        }

        if (width == height && width > threshold) {
            newWidth = threshold;
            //noinspection SuspiciousNameCombination
            newHeight = newWidth;
        }

        if (width == height && width <= threshold) {
            //the bitmap is already smaller than our required dimension, no need to resize it
            return bitmap;
        }

        return getResizedBitmap(bitmap, newWidth, newHeight, isNecessaryToKeepOrig);
    }

    private static Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight, boolean isNecessaryToKeepOrig) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;

        Bitmap resizedBitmap = resizeBitmap(bm, Math.min(scaleHeight, scaleWidth));

        if (!isNecessaryToKeepOrig && bm != resizedBitmap) { // Don't recycle if the result is the same object as the source
            bm.recycle();
        }
        return resizedBitmap;
    }

    private static Bitmap resizeBitmap(@NonNull final Bitmap src, float targetScale) {
        ImmutablePair<Integer, Float> resizeParams = computeResizeParams(targetScale);
        Timber.d(">> resizing successively to scale %s", resizeParams.right);
        return successiveResize(src, resizeParams.left);
    }

    /**
     * Compute resizing parameters according to the given target scale
     *
     * @param targetScale target scale of the image to display (% of the raw dimensions)
     * @return Pair containing
     * - First : Number of half-resizes to perform
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

    private static Bitmap successiveResize(@NonNull final Bitmap src, int resizeNum) {
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

    /**
     * Indicates if the picture needs to be rotated 90°, according to the given picture proportions (auto-rotate feature)
     * The goal is to align the picture's proportions with the phone screen's proportions
     *
     * @param screenWidth  Screen width
     * @param screenHeight Screen height
     * @param width        Picture width
     * @param height       Picture height
     * @return True if the picture needs to be rotated 90°
     */
    public static boolean needsRotating(int screenWidth, int screenHeight, int width, int height) {
        boolean isSourceSquare = (Math.abs(height - width) < width * 0.1);
        if (isSourceSquare) return false;

        boolean isSourceLandscape = (width > height * 1.33);
        boolean isScreenLandscape = (screenWidth > screenHeight * 1.33);
        return (isSourceLandscape != isScreenLandscape);
    }
}
