package me.devsaki.hentoid.util.file;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.ISeekableStream;
import net.sf.sevenzipjbinding.SevenZipException;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;

import timber.log.Timber;

// https://stackoverflow.com/a/28805474/8374722; https://stackoverflow.com/questions/28897329/documentfile-randomaccessfile
public class DocumentFileRandomInStream implements IInStream {

    private ContentResolver contentResolver;
    private Uri uri;

    private ParcelFileDescriptor pfdInput = null;
    private FileInputStream stream = null;

    private long streamSize;
    private long position;

    public DocumentFileRandomInStream(@NonNull final Context context, @NonNull final Uri uri) {
        try {
            this.contentResolver = context.getContentResolver();
            this.uri = uri;
            openUri();
            streamSize = stream.getChannel().size();
        } catch (IOException e) {
            Timber.e(e);
        }
    }

    private void openUri() throws IOException {
        if (stream != null) stream.close();
        if (pfdInput != null) pfdInput.close();

        pfdInput = contentResolver.openFileDescriptor(uri, "r");
        if (pfdInput != null)
            stream = new FileInputStream(pfdInput.getFileDescriptor());
    }

    @Override
    public long seek(long offset, int seekOrigin) throws SevenZipException {
        long seekDelta = 0;
        if (seekOrigin == ISeekableStream.SEEK_CUR) seekDelta = offset;
        else if (seekOrigin == ISeekableStream.SEEK_SET) seekDelta = offset - position;
        else if (seekOrigin == ISeekableStream.SEEK_END)
            seekDelta = streamSize + offset - position;

        if (position + seekDelta > streamSize) position = streamSize;

        if (seekDelta != 0) {
            try {
                if (seekDelta < 0) {
                    openUri();
                    skipNBytes(position + seekDelta);
                } else {
                    skipNBytes(seekDelta);
                }
            } catch (IOException e) {
                throw new SevenZipException(e);
            }
        }
        position += seekDelta;
        Timber.d("position %s", position);
        return position;
    }

    // Taken from Java14's InputStream
    // as basic skip is limited by the size of its buffer
    private void skipNBytes(long n) throws IOException {
        if (n > 0) {
            long ns = stream.skip(n);
            if (ns < n) { // skipped too few bytes
                // adjust number to skip
                n -= ns;
                // read until requested number skipped or EOS reached
                while (n > 0 && stream.read() != -1) {
                    n--;
                }
                // if not enough skipped, then EOFE
                if (n != 0) {
                    throw new EOFException();
                }
            } else if (ns != n) { // skipped negative or too many bytes
                throw new IOException("Unable to skip exactly");
            }
        }
    }

    @Override
    public int read(byte[] bytes) throws SevenZipException {
        try {
            int result = stream.read(bytes);
            position += result;
            if (result != bytes.length)
                Timber.w("diff %s expected; %s read", bytes.length, result);
            if (result < 0) result = 0;
            return result;
        } catch (IOException e) {
            throw new SevenZipException(e);
        }
    }

    @Override
    public void close() throws IOException {
        stream.close();
        pfdInput.close();
    }
}