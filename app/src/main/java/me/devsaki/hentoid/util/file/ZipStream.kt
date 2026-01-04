package me.devsaki.hentoid.util.file

import android.content.Context
import android.net.Uri
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readLongLe
import kotlinx.io.readString
import kotlinx.io.readUIntLe
import kotlinx.io.readUShortLe
import me.devsaki.hentoid.util.byteArrayOfInts
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException

private val FILE = byteArrayOfInts(0x50, 0x4B, 0x03, 0x04)
private val TOCFILE = byteArrayOfInts(0x50, 0x4B, 0x01, 0x02)
private val END64 = byteArrayOfInts(0x50, 0x4B, 0x06, 0x06)
private val END = byteArrayOfInts(0x50, 0x4B, 0x05, 0x06)
private val EXTRA64 = byteArrayOfInts(0x01, 0x00)

/**
 * Assume and force Zip64
 * Assume not multiple files ("disks")
 */
class ZipStream(context: Context, val archiveUri: Uri) {

    val is64: Boolean
    val cdrOffset: Long
    val cdrSize: Int
    val cdrCount: Long
    val cdrEndOffset: Long
    val allNotCompressed: Boolean

    val entries = ArrayList<ZipEntry>()

    init {
        // Read Zip file; build index
        val fileSize = fileSizeFromUri(context, archiveUri)
        getInputStream(context, archiveUri).use { fis ->
            // Find central directory footer
            val footerData = ByteArray(242) // 114 + 128 for optional comments
            val footerDataOffset = fileSize - footerData.size
            fis.skip(footerDataOffset)
            fis.read(footerData)
            var end = findSequencePosition(footerData, 0, END64)
            if (-1 == end) {
                end = findSequencePosition(footerData, 0, END)
                if (-1 == end) throw IOException("Invalid ZIP file : END not found")
                is64 = false
            } else {
                is64 = true
            }
            cdrEndOffset = footerDataOffset + end
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
                        cdrOffset = it.readLongLe()
                        // EOCD locator (not useful here)
                    } else {
                        // Non-64 EOCDR
                        it.skip(6) // Disk and count info
                        cdrCount = it.readUShortLe().toLong()
                        cdrSize = it.readUIntLe().toInt()
                        cdrOffset = it.readUIntLe().toLong()
                    }
                }
            }
        }
        Timber.d("$cdrCount $cdrSize $cdrOffset")

        // TODO read archives with corrupted table of contents, starting from file entries

        // Start with a brand new file stream to avoid mark/reset nightmares
        var hasOneCompressed = false
        var corruptedToC = false
        getInputStream(context, archiveUri).use { fis ->
            fis.asSource().buffered().use {
                // Read central directory
                fis.skip(cdrOffset)
                for (i in 1..cdrCount) {
                    var id = it.readByteArray(2)
                    if (!id.contentEquals(TOCFILE)) {
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
                            if (id.contentEquals(EXTRA64)) {
                                uncompressedSize = it.readLongLe()
                                it.skip(8) // Compressed size
                                lfhOffset = it.readLongLe()
                            } else {
                                it.skip(size)
                            }
                            read += size
                        } while (read <= extraDataLength)
                    }

                    entries.add(
                        ZipEntry(name, uncompressedSize, lfhOffset)
                    )
                    // Don't read comments
                }
            }
        }

        // Try parsing all file entries if table of contents is corrupted
        if (corruptedToC) {
            Timber.d("Corrupted table of contents; trying to parse file entries")
            entries.clear()
            getInputStream(context, archiveUri).use { fis ->
                fis.asSource().buffered().use {
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
                                if (id.contentEquals(EXTRA64)) {
                                    uncompressedSize = it.readLongLe()
                                    compressedSize = it.readLongLe()
                                } else {
                                    it.skip(size)
                                }
                                read += size
                            } while (read <= extraDataLength)
                        }
                        entries.add(
                            ZipEntry(name, uncompressedSize, offset)
                        )
                        it.skip(compressedSize)
                        offset += (30 + nameLength + extraDataLength + compressedSize)
                        Timber.d("offset $offset (size=$compressedSize)")

                        id = it.readByteArray(4)
                    } while (id.contentEquals(FILE))
                }
            }
        } // CorruptedToC

        entries.forEachIndexed { i, e ->
            Timber.d("$i ${e.path} (${e.isFolder}) ${e.size} @${e.lhOffset}")
        }

        allNotCompressed = !hasOneCompressed
    }

    data class ZipEntry(
        val path: String,
        val size: Long,
        val lhOffset: Long
    ) {
        val isFolder: Boolean
            get() = path.endsWith("/")
    }
}