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

    // If format is supported by Android, true if animated (animated GIF, APNG, animated WEBP); false if not
    // TODO complete doc
    boolean isImageAnimated(byte[] binary) {
        if (binary.length < 400) return false;

        switch (getMimeTypeFromPictureBinary(binary)) {
            case MIME_IMAGE_APNG:
                return true;
            case MIME_IMAGE_GIF:
                return FileHelper.findSequencePosition(binary, 0, "NETSCAPE".getBytes(CHARSET_LATIN_1), 400) > -1;
            case MIME_IMAGE_WEBP:
                return FileHelper.findSequencePosition(binary, 0, "ANIM".getBytes(CHARSET_LATIN_1), 400) > -1;
            default:
                return false;
        }
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
}
