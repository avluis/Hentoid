package me.devsaki.hentoid.customssiv.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

internal const val FILE_IO_BUFFER_SIZE = 32 * 1024

/**
 * Return the position of the given sequence in the given data array
 *
 * @param data       Data where to find the sequence
 * @param initialPos Initial position to start from
 * @param sequence   Sequence to look for
 * @param limit      Limit not to cross (in bytes counted from the initial position); 0 for unlimited
 * @return Position of the sequence in the data array; -1 if not found within the given initial position and limit
 */
internal fun findSequencePosition(
    data: ByteArray,
    initialPos: Int,
    sequence: ByteArray,
    limit: Int
): Int {
    var iSequence = 0

    if (initialPos < 0 || initialPos > data.size) return -1

    val remainingBytes = if ((limit > 0)) min((data.size - initialPos).toDouble(), limit.toDouble())
        .toInt() else data.size

    for (i in initialPos until remainingBytes) {
        if (sequence[iSequence] == data[i]) iSequence++
        else if (iSequence > 0) iSequence = 0

        if (sequence.size == iSequence) return i - sequence.size
    }

    // Target sequence not found
    return -1
}

/**
 * Indicate whether the file at the given Uri exists or not
 *
 * @param context Context to be used
 * @param fileUri Uri to the file whose existence is to check
 * @return True if the given Uri points to an existing file; false instead
 */
internal fun fileExists(context: Context, fileUri: Uri): Boolean {
    if (ContentResolver.SCHEME_FILE == fileUri.scheme) {
        val path = fileUri.path
        return if (path != null) File(path).exists()
        else false
    } else {
        val doc = getFileFromSingleUri(context, fileUri)
        return (doc != null)
    }
}

/**
 * Create an InputStream opened the file at the given Uri
 *
 * @param context Context to use
 * @param fileUri Uri to open the InputStream on
 * @return New InputStream opened on the given file
 * @throws IOException In case something horrible happens during I/O
 */
@Throws(IOException::class, IllegalArgumentException::class)
internal fun getInputStream(context: Context, fileUri: Uri): InputStream {
    return context.contentResolver.openInputStream(fileUri)
        ?: throw IOException("Input stream not found for $fileUri")
}

internal fun getFileFromSingleUri(context: Context, uri: Uri): DocumentFile? {
    val result = DocumentFile.fromSingleUri(context, uri)
    return if (null == result || !result.exists()) null
    else result
}