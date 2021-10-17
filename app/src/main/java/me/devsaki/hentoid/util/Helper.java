package me.devsaki.hentoid.util;

import static android.content.Context.CLIPBOARD_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.InsetDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nonnull;

import io.reactivex.disposables.Disposable;
import io.whitfin.siphash.SipHasher;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.json.JsonContentCollection;
import timber.log.Timber;

/**
 * Created by avluis on 06/05/2016.
 * Generic utility class
 */
public final class Helper {

    private Helper() {
        throw new IllegalStateException("Utility class");
    }

    private static final byte[] SIP_KEY = "0123456789ABCDEF".getBytes();


    /**
     * Retreives the given dimension value as DP, not pixels
     *
     * @param context Context to use to access resources
     * @param id      Dimension resource ID to get the value from
     * @return Given dimension value as DP
     */
    public static int dimensAsDp(@NonNull final Context context, @DimenRes int id) {
        return (int) (context.getResources().getDimension(id) / context.getResources().getDisplayMetrics().density);
    }

    /**
     * Create a Collections.List from the given array of primitive values
     *
     * @param input Array of primitive values to transform
     * @return Given values contained in a Collections.List
     */
    public static List<Long> getListFromPrimitiveArray(long[] input) {
        List<Long> list = new ArrayList<>(input.length);
        for (long n : input) list.add(n);
        return list;
    }

    /**
     * Create a Collections.List from the given array of primitive values
     *
     * @param input Array of primitive values to transform
     * @return Given values contained in a Collections.List
     */
    public static List<Integer> getListFromPrimitiveArray(int[] input) {
        List<Integer> list = new ArrayList<>(input.length);
        for (int n : input) list.add(n);
        return list;
    }

    /**
     * Create an array of primitive types from the given List of values
     *
     * @param input List of values to transform
     * @return Given values as an array of primitive types
     */
    public static long[] getPrimitiveLongArrayFromList(List<Long> input) {
        long[] ret = new long[input.size()];
        Iterator<Long> iterator = input.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next();
        }
        return ret;
    }

    public static int[] getPrimitiveLongArrayFromInt(Set<Integer> input) {
        int[] ret = new int[input.size()];
        Iterator<Integer> iterator = input.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next();
        }
        return ret;
    }

    /**
     * Inclusively coerce the given value between the given min and max values
     *
     * @param value Value to coerce
     * @param min   Min limit (inclusive)
     * @param max   Max limit (inclusive)
     * @return Given value inclusively coerced between the given min and max
     */
    public static float coerceIn(float value, float min, float max) {
        if (value < min) return min;
        else return Math.min(value, max);
    }

    /**
     * Duplicate the given InputStream as many times as given
     *
     * @param stream           Initial InputStream to duplicate
     * @param numberDuplicates Number of duplicates to create
     * @return List containing the given number of duplicated InputStreams
     * @throws IOException If anything goes wrong during the duplication
     */
    public static List<InputStream> duplicateInputStream(@Nonnull InputStream stream, int numberDuplicates) throws IOException {
        List<InputStream> result = new ArrayList<>();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buffer = new byte[FileHelper.FILE_IO_BUFFER_SIZE];
        int len;
        while ((len = stream.read(buffer)) > -1) baos.write(buffer, 0, len);
        baos.flush();

        for (int i = 0; i < numberDuplicates; i++)
            result.add(new ByteArrayInputStream(baos.toByteArray()));

        return result;
    }

    /**
     * Copy plain text to the device's clipboard
     *
     * @param context Context to be used
     * @param text    Text to copy
     * @return True if the copy has succeeded; false if not
     */
    public static boolean copyPlainTextToClipboard(@NonNull Context context, @NonNull String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("book URL", text);
            clipboard.setPrimaryClip(clip);
            return true;
        } else return false;
    }

    /**
     * Fix for a crash on 5.1.1
     * https://stackoverflow.com/questions/41025200/android-view-inflateexception-error-inflating-class-android-webkit-webview
     * As fallback solution _only_ since it breaks other stuff in the webview (choice in SELECT tags for instance)
     *
     * @param context Context to fix
     * @return Fixed context
     */
    public static Context getFixedContext(Context context) {
        return context.createConfigurationContext(new Configuration());
    }

    /**
     * Crashes if called on the UI thread
     * To be used as a marker wherever processing in a background thread is mandatory
     */
    public static void assertNonUiThread() {
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            throw new IllegalStateException("This should not be run on the UI thread");
        }
    }

    /// <summary>
    /// Format the given duration using the following format
    ///     DDdHH:MM:SS
    ///
    ///  Where
    ///     DD is the number of days, if applicable (i.e. durations of less than 1 day won't display the "DDd" part)
    ///     HH is the number of hours, if applicable (i.e. durations of less than 1 hour won't display the "HH:" part)
    ///     MM is the number of minutes
    ///     SS is the number of seconds
    /// </summary>
    /// <param name="seconds">Duration to format (in seconds)</param>
    /// <returns>Formatted duration according to the abovementioned convention</returns>

    /**
     * Format the given duration using the HH:MM:SS format
     *
     * @param ms Duration to format, in milliseconds
     * @return FormattedDuration
     */
    public static String formatDuration(long ms) {
        long seconds = (long) Math.floor(ms / 1000f);
        int h = (int) Math.floor(seconds / 3600f);
        int m = (int) Math.floor((seconds - 3600f * h) / 60);
        long s = seconds - (60L * m) - (3600L * h);

        String hStr = String.valueOf(h);
        if (1 == hStr.length()) hStr = "0" + hStr;
        String mStr = String.valueOf(m);
        if (1 == mStr.length()) mStr = "0" + mStr;
        String sStr = String.valueOf(s);
        if (1 == sStr.length()) sStr = "0" + sStr;

        if (h > 0)
            return hStr + ":" + mStr + ":" + sStr;
        else
            return mStr + ":" + sStr;
    }

    /**
     * Indicate whether the given View's context is usable by Glide
     *
     * @param view View whose Context to test
     * @return True if the given View's context is usable by Glide; false if not
     */
    public static boolean isValidContextForGlide(final View view) {
        return isValidContextForGlide(view.getContext());
    }

    /**
     * Indicate whether the given Context is usable by Glide
     *
     * @param context Context to test
     * @return True if the given Context is usable by Glide; false if not
     */
    public static boolean isValidContextForGlide(final Context context) {
        if (context == null) {
            return false;
        }
        if (context instanceof Activity) {
            final Activity activity = (Activity) context;
            return !activity.isDestroyed() && !activity.isFinishing();
        }
        return true;
    }

    /**
     * Build an 64-bit SIP hash from the given data
     *
     * @param data Data to hash
     * @return Hash built from the given data
     */
    public static long hash64(@NonNull final byte[] data) {
        return SipHasher.hash(SIP_KEY, data);
    }

    /**
     * Compute the weighted average of the given operands
     * - Left part is the value
     * - Right part is the coefficient
     *
     * @param operands List of (value, coefficient) pairs
     * @return Weigthed average of the given operands; 0 if uncomputable
     */
    public static float weightedAverage(List<Pair<Float, Float>> operands) {
        if (operands.isEmpty()) return 0;

        float numerator = 0;
        float denominator = 0;
        for (Pair<Float, Float> operand : operands) {
            numerator += (operand.first * operand.second);
            denominator += operand.second;
        }
        return (denominator > 0) ? numerator / denominator : 0;
    }

    // TODO doc
    public static class LifecycleRxCleaner implements LifecycleObserver {

        private final Disposable disposable;

        public LifecycleRxCleaner(Disposable disposable) {
            this.disposable = disposable;
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        private void onDestroy() {
            disposable.dispose();
        }

        public void publish() {
            Handler autoCleanHandler = new Handler(Looper.getMainLooper());
            autoCleanHandler.post(() -> ProcessLifecycleOwner.get().getLifecycle().addObserver(this));
        }
    }

    // TODO doc
    public static long generateIdForPlaceholder() {
        long result = new Random().nextLong();
        // Make sure nothing collides with an actual ID; nobody has 1M books; it should be fine
        while (result < 1e6) result = new Random().nextLong();
        return result;
    }

    // TODO doc
    // Inspired by https://material.io/components/menus/android#dropdown-menus
    @SuppressLint("RestrictedApi")
    public static void tryShowMenuIcons(@NonNull Context context, @NonNull Menu menu) {
        try {
            if (menu instanceof MenuBuilder) {
                MenuBuilder builder = (MenuBuilder) menu;
                builder.setOptionalIconsVisible(true);
                int iconMarginPx = (int) context.getResources().getDimension(R.dimen.icon_margin);
                for (MenuItem item : builder.getVisibleItems()) {
                    if (item.getIcon() != null)
                        item.setIcon(new InsetDrawable(item.getIcon(), iconMarginPx, 0, iconMarginPx, 0));
                }
            }
        } catch (Exception e) {
            Timber.i(e);
        }
    }

    /**
     * Update the JSON file that stores bookmarks with the current bookmarks
     *
     * @param context Context to be used
     * @param dao     DAO to be used
     * @return True if the bookmarks JSON file has been updated properly; false instead
     */
    public static boolean updateBookmarksJson(@NonNull Context context, @NonNull CollectionDAO dao) {
        Helper.assertNonUiThread();
        List<SiteBookmark> bookmarks = dao.selectAllBookmarks();

        JsonContentCollection contentCollection = new JsonContentCollection();
        contentCollection.setBookmarks(bookmarks);

        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(context, Preferences.getStorageUri());
        if (null == rootFolder) return false;

        try {
            JsonHelper.jsonToFile(context, contentCollection, JsonContentCollection.class, rootFolder, Consts.BOOKMARKS_JSON_FILE_NAME);
        } catch (IOException | IllegalArgumentException e) {
            // NB : IllegalArgumentException might happen for an unknown reason on certain devices
            // even though all the file existence checks are in place
            // ("Failed to determine if primary:.Hentoid/queue.json is child of primary:.Hentoid: java.io.FileNotFoundException: Missing file for primary:.Hentoid/queue.json at /storage/emulated/0/.Hentoid/queue.json")
            Timber.e(e);
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.recordException(e);
            return false;
        }
        return true;
    }
}
