package me.devsaki.hentoid.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.View;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.annimon.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import io.reactivex.disposables.Disposable;
import io.whitfin.siphash.SipHasher;

import static android.content.Context.CLIPBOARD_SERVICE;

/**
 * Created by avluis on 06/05/2016.
 * String-related utility class
 */
public final class StringHelper {

    private StringHelper() {
        throw new IllegalStateException("Utility class");
    }

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");


    /**
     * Return the given string formatted with a capital letter as its first letter
     *
     * @param s String to format
     * @return Given string formatted with a capital letter as its first letter
     */
    public static String capitalizeString(String s) {
        if (s == null || s.isEmpty()) return s;
        else if (s.length() == 1) return s.toUpperCase();
        else return s.substring(0, 1).toUpperCase() + s.toLowerCase().substring(1);
    }

    /**
     * Transform the given int to format with a given length
     * - If the given length is shorter than the actual length of the string, it will be truncated
     * - If the given length is longer than the actual length of the string, it will be left-padded with the character 0
     *
     * @param value  String to transform
     * @param length Target length of the final string
     * @return Reprocessed string of given length, according to rules documented in the method description
     */
    public static String formatIntAsStr(int value, int length) {
        String result = String.valueOf(value);

        if (result.length() > length) {
            result = result.substring(0, length);
        } else if (result.length() < length) {
            result = String.format("%1$" + length + "s", result).replace(' ', '0');
        }

        return result;
    }

    /**
     * Indicate of the given string is present as a word inside the given expression
     * "present as a word" means present as a substring separated from other substrings by separating characters
     *
     * @param toDetect   String whose presence to detect within the given expression
     * @param expression Expression where the given string will be searched for
     * @return True if the given string is present as a word inside the given expression; false if not
     */
    public static boolean isPresentAsWord(@NonNull final String toDetect, @NonNull final String expression) {
        String[] words = expression.split("\\W");
        return Stream.of(words).anyMatch(w -> w.equalsIgnoreCase(toDetect));
    }

    /**
     * Determine whether the given string represents a numeric value or not
     *
     * @param str Value to test
     * @return True if the given value is numeric (including negative and decimal numbers); false if not
     */
    public static boolean isNumeric(@NonNull final String str) {
        Matcher m = NUMERIC_PATTERN.matcher(str);
        return m.matches();
    }

    /**
     * Remove all non-printable characters from the given string
     * https://stackoverflow.com/a/18603020/8374722
     *
     * @param s String to cleanup
     * @return Given string stripped from all its non-printable characters
     */
    public static String removeNonPrintableChars(@Nullable final String s) {
        if (null == s || s.isEmpty()) return "";

        StringBuilder newString = new StringBuilder(s.length());
        for (int offset = 0; offset < s.length(); ) {
            int codePoint = s.codePointAt(offset);
            offset += Character.charCount(codePoint);

            // Replace invisible control characters and unused code points
            switch (Character.getType(codePoint)) {
                case Character.CONTROL:     // \p{Cc}
                case Character.FORMAT:      // \p{Cf}
                case Character.PRIVATE_USE: // \p{Co}
                case Character.SURROGATE:   // \p{Cs}
                case Character.UNASSIGNED:  // \p{Cn}
                    // Don't do anything; these are characters we don't want in the new string
                    break;
                default:
                    newString.append(Character.toChars(codePoint));
                    break;
            }
        }
        return newString.toString();
    }

    /**
     * Unescape all escaped characters from the given string (Java convention)
     *
     * @param s String to be cleaned up
     * @return Given string where all escaped characters have been unescaped
     */
    public static String replaceEscapedChars(@NonNull final String s) {
        return StringEscapeUtils.unescapeJava(s);
    }

    /**
     * Return the given value, or an empty string if it's null
     *
     * @param s String to protect if its value its null
     * @return The given value, or an empty string if it's null
     */
    public static String protect(@Nullable String s) {
        return (null == s) ? "" : s;
    }
}
