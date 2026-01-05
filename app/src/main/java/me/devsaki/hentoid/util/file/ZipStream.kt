package me.devsaki.hentoid.util.file

import android.content.Context
import android.net.Uri
import kotlinx.io.Sink
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readLongLe
import kotlinx.io.readString
import kotlinx.io.readUIntLe
import kotlinx.io.readUShortLe
import kotlinx.io.writeUIntLe
import kotlinx.io.writeULongLe
import kotlinx.io.writeUShortLe
import me.devsaki.hentoid.util.byteArrayOfInts
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.Date


private val FILE = byteArrayOfInts(0x50, 0x4B, 0x03, 0x04)
private val TOCFILE = byteArrayOfInts(0x50, 0x4B, 0x01, 0x02)
private val TOCEND64 = byteArrayOfInts(0x50, 0x4B, 0x06, 0x06)
private val TOCEND64_LOCATOR = byteArrayOfInts(0x50, 0x4B, 0x06, 0x07)
private val TOCEND = byteArrayOfInts(0x50, 0x4B, 0x05, 0x06)
private val FILE_EXTRA64 = byteArrayOfInts(0x01, 0x00)

/**
 * DOS time constant for representing timestamps before 1980.
 */
private const val DOSTIME_BEFORE_1980 = (1 shl 21) or (1 shl 16)

/**
 * Assume and force Zip64
 * Assume not multiple files ("disks")
 */
class ZipStream(context: Context, archiveUri: Uri, append: Boolean) : Closeable {
    // TODO manage append

    var tocOffset: Long
    val allNotCompressed: Boolean

    val records = ArrayList<ZipRecord>()

    var currentRecord: ZipRecord? = null
    var currentOffset = 0L

    val sink: Sink

    init {
        // Read Zip file; build index
        var cdrCount = 0L
        var is64 = false
        var cdrSize = 0

        val fileSize = fileSizeFromUri(context, archiveUri)

        if (0L == fileSize) { // Brand new file
            tocOffset = 0
            allNotCompressed = false
        } else { // Existing file
            getInputStream(context, archiveUri).use { fis ->
                // Find central directory footer
                val footerData = ByteArray(242) // 114 + 128 for optional comments
                val footerDataOffset = fileSize - footerData.size
                fis.skip(footerDataOffset)
                fis.read(footerData)
                var end = findSequencePosition(footerData, 0, TOCEND64)
                if (-1 == end) {
                    end = findSequencePosition(footerData, 0, TOCEND)
                    if (-1 == end) throw IOException("Invalid ZIP file : END not found")
                } else {
                    is64 = true
                }
                val cdrEndOffset = footerDataOffset + end
                Timber.d("$cdrEndOffset = $footerDataOffset + $end ($is64)")

                // Read central directory footer
                ByteArrayInputStream(footerData, end, footerData.size - end).use { bais ->
                    bais.asSource().buffered().use {
                        it.skip(4) // Header
                        if (is64) {
                            // Zip64 EOCDR
                            val ecdrSize = it.readLongLe()
                            it.skip(2) // Version (creator)
                            val versionViewer = it.readUShortLe()
                            if (versionViewer > 50u) throw UnsupportedOperationException("ZIP version not supported : $versionViewer")
                            it.skip(8) // Disk info
                            it.skip(8) // Number of CDRs on disk
                            cdrCount = it.readLongLe()
                            cdrSize = it.readLongLe().toInt()
                            tocOffset = it.readLongLe()
                            // EOCD locator (not useful here)
                        } else {
                            // Non-64 EOCDR
                            it.skip(6) // Disk and count info
                            cdrCount = it.readUShortLe().toLong()
                            cdrSize = it.readUIntLe().toInt()
                            tocOffset = it.readUIntLe().toLong()
                        }
                    }
                }
            }
            Timber.d("$cdrCount $cdrSize $tocOffset")

            // Start with a brand new file stream to avoid mark/reset nightmares
            var hasOneCompressed = false
            var corruptedToC = false
            getInputStream(context, archiveUri).asSource().buffered().use {
                // Read central directory
                it.skip(tocOffset)
                for (i in 1..cdrCount) {
                    var id = it.readByteArray(4)
                    if (!id.contentEquals(TOCFILE)) {
                        Timber.d("Corrupted ToC @ $i (${records.size} records in)")
                        corruptedToC = true
                        break
                    }
                    it.skip(4) // Version (creator and viewer)
                    val flags = it.readUShortLe()
                    if (1u == flags % 2u) throw UnsupportedOperationException("Encrypted ZIP entries are not supported")
                    val compressionMode = it.readUShortLe()
                    if (compressionMode > 0u) hasOneCompressed = true
                    it.skip(2) // Time
                    it.skip(2) // TODO Date
                    it.skip(4) // CRC32
                    var uncompressedSize = it.readUIntLe().toLong()
                    it.skip(4) // Compressed size
                    val nameLength = it.readUShortLe().toLong()
                    val extraDataLength = it.readUShortLe().toInt()
                    it.skip(2) // Comment length
                    it.skip(2) // Disk number
                    it.skip(6) // Internal and external attributes
                    var lfhOffset = it.readUIntLe().toLong()
                    val name = it.readString(nameLength, Charsets.UTF_8)
                    // Extra data
                    if (extraDataLength > 0) {
                        var read = 0L
                        do {
                            id = it.readByteArray(2)
                            val size = it.readUShortLe().toLong()
                            if (id.contentEquals(FILE_EXTRA64)) {
                                uncompressedSize = it.readLongLe()
                                it.skip(8) // Compressed size
                                lfhOffset = it.readLongLe()
                            } else {
                                it.skip(size)
                            }
                            read += (4 + size)
                        } while (read < extraDataLength)
                    }

                    records.add(
                        ZipRecord(name, uncompressedSize, lfhOffset)
                    )
                    // Don't read comments
                } // cdrCount
            } // FileInputStream

            // Try parsing all file entries if table of contents is corrupted
            if (corruptedToC) {
                Timber.d("Corrupted table of contents; trying to parse file entries")
                records.clear()
                getInputStream(context, archiveUri).asSource().buffered().use {
                    var offset = 0L
                    var id = it.readByteArray(4)
                    do {
                        it.skip(2) // Version (viewer)
                        val flags = it.readUShortLe()
                        if (1u == flags % 2u) throw UnsupportedOperationException("Encrypted ZIP entries are not supported")
                        val compressionMode = it.readUShortLe()
                        if (compressionMode > 0u) hasOneCompressed = true
                        it.skip(2) // Time
                        it.skip(2) // TODO Date
                        it.skip(4) // CRC32
                        var uncompressedSize = it.readUIntLe().toLong()
                        var compressedSize = it.readUIntLe().toLong()
                        val nameLength = it.readUShortLe().toLong()
                        val extraDataLength = it.readUShortLe().toInt()
                        val name = it.readString(nameLength, Charsets.UTF_8)
                        // Extra data
                        if (extraDataLength > 0) {
                            var read = 0L
                            do {
                                id = it.readByteArray(2)
                                val size = it.readUShortLe().toLong()
                                if (id.contentEquals(FILE_EXTRA64)) {
                                    uncompressedSize = it.readLongLe()
                                    compressedSize = it.readLongLe()
                                } else {
                                    it.skip(size)
                                }
                                read += (4 + size)
                            } while (read < extraDataLength)
                        }
                        records.add(
                            ZipRecord(name, uncompressedSize, offset)
                        )
                        it.skip(compressedSize)
                        offset += (30 + nameLength + extraDataLength + compressedSize)

                        id = it.readByteArray(4)
                    } while (id.contentEquals(FILE))
                    Timber.d("Ended @ $offset")

                    if (id.contentEquals(TOCFILE)) tocOffset = offset
                }
            } // CorruptedToC

            allNotCompressed = !hasOneCompressed
        } // File size

        Timber.d("TocOffset $tocOffset")

        records.forEachIndexed { i, e ->
            Timber.d("RECORD $i ${e.path} (${e.isFolder}) ${e.size} @${e.offset}")
        }


        val outStream = getOutputStream(context, archiveUri, append)
            ?: throw IOException("Couldn't open for output : $archiveUri")

        if (append) currentOffset = fileSize

        /*
        // Open in normal mode
        var outStream = getOutputStream(context, archiveUri)
            ?: throw IOException("Couldn't open for output : $archiveUri")

        // Skip to ToC offset
        if (append) {
            var hasSkipped = false
            try {
                if (outStream is FileOutputStream) {
                    val ch: FileChannel = outStream.getChannel()
                    ch.position(tocOffset)
                    hasSkipped = true
                }
            } catch (e: Exception) {
                Timber.d(e)
            }
            if (!hasSkipped) {
                Timber.d("Couldn't skip; opening in APPEND mode")
                outStream = getOutputStream(context, archiveUri, true)
                    ?: throw IOException("Couldn't open for output : $archiveUri")
            }
        }

         */

        sink = outStream.asSink().buffered()
    }

    /**
     * Write file record descriptor using STORED mode and ZIP64 structure
     */
    fun putStoredRecord(path: String, size: Long, crc: Long) {
        sink.write(FILE)
        sink.writeUShortLe(45u) // Version for ZIP64
        sink.writeUShortLe(0u) // Flag
        sink.writeUShortLe(0u) // STORED mode
        val dosTime = javaToExtendedDosTime(java.time.Instant.now().toEpochMilli()).first
        sink.writeUIntLe(dosTime.toUInt()) // Time & Date
        sink.writeUIntLe(crc.toUInt())
        sink.writeUIntLe(UInt.MAX_VALUE) // Uncompressed size for ZIP64
        sink.writeUIntLe(UInt.MAX_VALUE) // Compressed size for ZIP64
        val nameBuffer = Charsets.UTF_8.encode(path)
        val nameData = ByteArray(nameBuffer.limit())
        nameBuffer.get(nameData)
        sink.writeUShortLe(nameData.size.toUShort())
        sink.writeUShortLe(20u) // Size of ZIP64 extra data
        sink.write(nameData)
        // ZIP64 extra data
        sink.write(FILE_EXTRA64)
        sink.writeUShortLe(16u) // Size of ZIP64 extra data (without headers)
        sink.writeULongLe(size.toULong())
        sink.writeULongLe(size.toULong()) // Compressed size is identical to uncompressed size as we use STORED mode

        currentRecord = ZipRecord(path, size, currentOffset, time = dosTime, crc = crc)
        currentOffset += (30 + nameData.size + 20)
    }

    /**
     * Write file record data without any compression / encryption (STORED mode)
     */
    fun transferData(s: InputStream) {
        currentRecord?.let {
            val size = sink.transferFrom(s.asSource())
            if (size != it.size) throw Exception("Transferred size ($size) is different than declared size (${it.size})")
            currentOffset += size
        }
    }

    /**
     * Close file record
     */
    fun closeRecord() {
        currentRecord?.let { e ->
            records.add(e)
            Timber.d("NEW RECORD ${e.path} (${e.isFolder}) ${e.size} @${e.offset}")
        }
        currentRecord = null
    }

    /**
     * Close the stream, writing the entire table of contents using STORED mode and ZIP64 structure
     */
    override fun close() {
        Timber.d("ZipStream : Closing")
        // Write brand new ToC
        val tocOffset = currentOffset
        var tocSize = 0L
        Timber.d("ZipStream : Writing ToC for ${records.size} records")
        records.forEach {
            sink.write(TOCFILE)
            sink.writeUShortLe(45u) // Version for ZIP64
            sink.writeUShortLe(45u) // Version for ZIP64
            sink.writeUShortLe(0u) // Flag
            sink.writeUShortLe(0u) // STORED mode
            sink.writeUIntLe(it.time.toUInt()) // Time & Date
            sink.writeUIntLe(it.crc.toUInt())
            sink.writeUIntLe(UInt.MAX_VALUE) // Uncompressed size for ZIP64
            sink.writeUIntLe(UInt.MAX_VALUE) // Compressed size for ZIP64
            val nameBuffer = Charsets.UTF_8.encode(it.path)
            val nameData = ByteArray(nameBuffer.limit())
            nameBuffer.get(nameData)
            sink.writeUShortLe(nameData.size.toUShort())
            sink.writeUShortLe(28u) // Size of ZIP64 extra data
            sink.writeUShortLe(0u) // Comment length
            sink.writeUShortLe(0u) // Disk number
            sink.writeUShortLe(0u) // Internal attrs
            sink.writeUIntLe(0u) // External attrs
            sink.writeUIntLe(UInt.MAX_VALUE) // Offset for ZIP64
            sink.write(nameData)
            // ZIP64 extra data
            sink.write(FILE_EXTRA64)
            sink.writeUShortLe(24u) // Size of ZIP64 extra data (without headers)
            sink.writeULongLe(it.size.toULong())
            sink.writeULongLe(it.size.toULong()) // Compressed size is identical to uncompressed size as we use STORED mode
            sink.writeULongLe(it.offset.toULong())
            tocSize += (46 + nameData.size + 28)
        }

        // Write ToC footer
        // Footer for ZIP64
        sink.write(TOCEND64)
        sink.writeULongLe(44u)
        sink.writeUShortLe(45u) // Version for ZIP64
        sink.writeUShortLe(45u) // Version for ZIP64
        sink.writeUIntLe(0u) // Disk number
        sink.writeUIntLe(0u) // Disk with central directory
        sink.writeULongLe(records.size.toULong()) // Number of records on this disk
        sink.writeULongLe(records.size.toULong()) // Total number of records
        sink.writeULongLe(tocSize.toULong()) // Size of ToC
        sink.writeULongLe(tocOffset.toULong()) // Offset of ToC

        // ZIP64 locator
        val eocdOffset = tocOffset + tocSize
        sink.write(TOCEND64_LOCATOR)
        sink.writeUIntLe(0u) // Disk with EOCD record
        sink.writeULongLe(eocdOffset.toULong()) // Offset of EOCD
        sink.writeUIntLe(1u) // Number of disks

        // Classic footer
        sink.write(TOCEND)
        sink.writeUShortLe(0u) // Disc number
        sink.writeUShortLe(0u) // Disc with central directory
        sink.writeUShortLe(UShort.MAX_VALUE) // Entries on disk for ZIP64
        sink.writeUShortLe(UShort.MAX_VALUE) // Total entries for ZIP64
        sink.writeUIntLe(UInt.MAX_VALUE) // Size of ToC for ZIP64
        sink.writeUIntLe(UInt.MAX_VALUE) // Offset of ToC for ZIP64
        sink.writeUShortLe(0u) // Comment length

        sink.flush()
        sink.close()
        currentOffset = 0
    }

    /**
     * Converts Java time to DOS time, encoding any milliseconds lost
     * in the conversion into the upper half of the returned long
     *
     * Adapted from java.util.zip.ZipUtils
     *
     * @param time milliseconds since epoch
     * @return
     *   first : DOS time
     *   second : 2s remainder
     */
    private fun javaToExtendedDosTime(time: Long): Pair<Int, Int> {
        if (time < 0) return Pair(DOSTIME_BEFORE_1980, 0)

        val dostime = javaToDosTime(time)
        return if (dostime != DOSTIME_BEFORE_1980) Pair(dostime, (time % 2000).toInt())
        else Pair(DOSTIME_BEFORE_1980, 0)
    }

    /**
     * Converts Java time to DOS time
     * Adapted from java.util.zip.ZipUtils
     */
    @Suppress("deprecation") // Use of date methods
    private fun javaToDosTime(time: Long): Int {
        val d = Date(time)
        val year = d.year + 1900
        if (year < 1980) return DOSTIME_BEFORE_1980
        return (year - 1980) shl 25 or ((d.month + 1) shl 21) or (d.date shl 16) or (d.hours shl 11) or (d.minutes shl 5) or (d.seconds shr 1)
    }

    data class ZipRecord(
        val path: String,
        val size: Long,
        val offset: Long,
        val time: Int = 0,
        val crc: Long = 0L
    ) {
        val isFolder: Boolean
            get() = path.endsWith("/")
    }
}