package me.devsaki.hentoid.customssiv.util

import kotlin.math.min

const val FILE_IO_BUFFER_SIZE = 32 * 1024

/**
 * Return the position of the given sequence in the given data array
 *
 * @param data       Data where to find the sequence
 * @param initialPos Initial position to start from
 * @param sequence   Sequence to look for
 * @param limit      Limit not to cross (in bytes counted from the initial position); 0 for unlimited
 * @return Position of the sequence in the data array; -1 if not found within the given initial position and limit
 */
internal fun findSequencePosition(data: ByteArray, initialPos: Int, sequence: ByteArray, limit: Int): Int {
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