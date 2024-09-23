package me.devsaki.hentoid.customssiv.util;

/**
 * Generic file-related utility class
 */
public class FileHelper {

    private FileHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static final int FILE_IO_BUFFER_SIZE = 32 * 1024;


    /**
     * Return the position of the given sequence in the given data array
     *
     * @param data       Data where to find the sequence
     * @param initialPos Initial position to start from
     * @param sequence   Sequence to look for
     * @param limit      Limit not to cross (in bytes counted from the initial position); 0 for unlimited
     * @return Position of the sequence in the data array; -1 if not found within the given initial position and limit
     */
    static int findSequencePosition(byte[] data, int initialPos, byte[] sequence, int limit) {
        int remainingBytes;
        int iSequence = 0;

        if (initialPos < 0 || initialPos > data.length) return -1;

        remainingBytes = (limit > 0) ? Math.min(data.length - initialPos, limit) : data.length;

        for (int i = initialPos; i < remainingBytes; i++) {
            if (sequence[iSequence] == data[i]) iSequence++;
            else if (iSequence > 0) iSequence = 0;

            if (sequence.length == iSequence) return i - sequence.length;
        }

        // Target sequence not found
        return -1;
    }
}
