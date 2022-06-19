package me.devsaki.hentoid.customssiv.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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


    private ImageHelper() {
        throw new IllegalStateException("Utility class");
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
    public static boolean isImageAnimated(byte[] data) {
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
}
