package me.devsaki.hentoid.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static android.graphics.Bitmap.Config.ARGB_8888;

/**
 * Created by Robb on 07/2020
 * Generic utility class
 */
public final class ImageHelper {

    private ImageHelper() {
        throw new IllegalStateException("Utility class");
    }

    private static FileHelper.NameFilter imageNamesFilter;
    private static final Charset CHARSET_LATIN_1 = StandardCharsets.ISO_8859_1;

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
            return "image/jpeg";
        else if ((byte) 0x89 == binary[0] && (byte) 0x50 == binary[1] && (byte) 0x4E == binary[2]) {
            // Detect animated PNG : To be recognized as APNG an 'acTL' chunk must appear in the stream before any 'IDAT' chunks
            int acTlPos = FileHelper.findSequencePosition(binary, 0, "acTL".getBytes(CHARSET_LATIN_1), (int) (binary.length * 0.2));
            if (acTlPos > -1) {
                long idatPos = FileHelper.findSequencePosition(binary, acTlPos, "IDAT".getBytes(CHARSET_LATIN_1), (int) (binary.length * 0.1));
                if (idatPos > -1) return "image/apng";
            }
            return "image/png";
        } else if ((byte) 0x47 == binary[0] && (byte) 0x49 == binary[1] && (byte) 0x46 == binary[2])
            return "image/gif";
        else if ((byte) 0x52 == binary[0] && (byte) 0x49 == binary[1] && (byte) 0x46 == binary[2] && (byte) 0x46 == binary[3]
                && (byte) 0x57 == binary[8] && (byte) 0x45 == binary[9] && (byte) 0x42 == binary[10] && (byte) 0x50 == binary[11])
            return "image/webp";
        else if ((byte) 0x42 == binary[0] && (byte) 0x4D == binary[1]) return "image/bmp";
        else return "image/*";
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
}
