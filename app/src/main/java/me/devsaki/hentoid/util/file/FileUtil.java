package me.devsaki.hentoid.util.file;

import static org.apache.commons.io.FileUtils.ONE_GB_BI;
import static org.apache.commons.io.FileUtils.ONE_KB_BI;
import static org.apache.commons.io.FileUtils.ONE_MB_BI;

import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

import me.devsaki.hentoid.R;
import timber.log.Timber;

/**
 * Methods for use by FileHelper
 */
class FileUtil {

    private FileUtil() {
        throw new IllegalStateException("Utility class");
    }

    static final DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getInstance(Locale.getDefault());
    static final char decimalSeparator = decimalFormat.getDecimalFormatSymbols().getDecimalSeparator();

    /**
     * Method ensures file creation from stream.
     *
     * @param stream - FileOutputStream.
     * @return true if all OK.
     */
    static boolean sync(@NonNull final FileOutputStream stream) {
        try {
            stream.getFD().sync();
            return true;
        } catch (IOException e) {
            Timber.e(e, "IO Error");
        }

        return false;
    }

    /**
     * Delete a file.
     *
     * @param file The file to be deleted.
     * @return true if successfully deleted or if the file does not exist.
     */
    static boolean deleteFile(@NonNull final File file) {
        return !file.exists() || deleteQuietly(file);
    }

    /**
     * Deletes a file, never throwing an exception. If file is a directory, delete it and all sub-directories.
     * <p>
     * The difference between File.delete() and this method are:
     * <ul>
     * <li>A directory to be deleted does not have to be empty.</li>
     * <li>No exceptions are thrown when a file or directory cannot be deleted.</li>
     * </ul>
     * <p>
     * Custom substitute for commons.io.FileUtils.deleteQuietly that works with devices that doesn't support File.toPath
     *
     * @param file file or directory to delete, can be {@code null}
     * @return {@code true} if the file or directory was deleted, otherwise
     * {@code false}
     */
    private static boolean deleteQuietly(final File file) {
        if (file == null) {
            return false;
        }
        try {
            if (file.isDirectory()) {
                tryCleanDirectory(file);
            }
        } catch (final Exception ignored) {
        }

        try {
            return file.delete();
        } catch (final Exception ignored) {
            return false;
        }
    }

    /**
     * Cleans a directory without deleting it.
     * <p>
     * Custom substitute for commons.io.FileUtils.cleanDirectory that supports devices without File.toPath
     *
     * @param directory directory to clean
     * @return true if directory has been successfully cleaned
     * @throws IOException in case cleaning is unsuccessful
     */
    static boolean tryCleanDirectory(@NonNull File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Failed to list content of " + directory);

        boolean isSuccess = true;

        for (File file : files) {
            if (file.isDirectory() && !tryCleanDirectory(file)) isSuccess = false;
            if (!file.delete() && file.exists()) isSuccess = false;
        }

        return isSuccess;
    }

    /**
     * Returns a human-readable version of the file size, where the input represents a specific number of bytes.
     * <p>
     * If the size is over 1GB, the size is rounded by places.
     * </p>
     * <p>
     * Similarly for the 1MB and 1KB boundaries.
     * </p>
     *
     * @param size   the number of bytes
     * @param places rounded decimal places
     * @param locale decimal separator locale
     * @return a human-readable display value (includes units - EB, PB, TB, GB, MB, KB or bytes)
     * @see <a href="https://issues.apache.org/jira/browse/IO-226">IO-226 - should the rounding be changed?</a>
     */
    // Directly copied from https://github.com/apache/commons-io/pull/74
    public static String byteCountToDisplayRoundedSize(final BigInteger size, final int places, final Resources res, final Locale locale) {
        if (size == null) return null;

        final long sizeInLong = size.longValue();
        final String formatPattern = "%." + places + "f";

        String displaySize;
        if (size.divide(ONE_GB_BI).compareTo(BigInteger.ZERO) > 0) {
            displaySize = String.format(locale, formatPattern, sizeInLong / ONE_GB_BI.doubleValue()) + " " + res.getString(R.string.u_gigabyte);
        } else if (size.divide(ONE_MB_BI).compareTo(BigInteger.ZERO) > 0) {
            displaySize = String.format(locale, formatPattern, sizeInLong / ONE_MB_BI.doubleValue()) + " " + res.getString(R.string.u_megabyte);
        } else if (size.divide(ONE_KB_BI).compareTo(BigInteger.ZERO) > 0) {
            displaySize = String.format(locale, formatPattern, sizeInLong / ONE_KB_BI.doubleValue()) + " " + res.getString(R.string.u_kilobyte);
        } else {
            displaySize = size + " " + res.getString(R.string.u_byte);
        }

        return displaySize.replaceFirst("[" +
                        decimalSeparator +
                        "]" +
                        new String(new char[places]).replace('\0', '0') +
                        " "
                , " ");
    }

    /**
     * Returns a human-readable version of the file size, where the input represents a specific number of bytes.
     *
     * @param size   the number of bytes
     * @param places rounded decimal places
     * @return a human-readable display value (includes units - GB, MB, KB or bytes)
     * @see <a href="https://issues.apache.org/jira/browse/IO-226">IO-226 - should the rounding be changed?</a>
     */
    public static String byteCountToDisplayRoundedSize(final Long size, final int places, final Resources res) {
        return byteCountToDisplayRoundedSize(BigInteger.valueOf(size), places, res, Locale.getDefault());
    }
}
