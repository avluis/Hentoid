package me.devsaki.hentoid.util.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.util.assertNonUiThread
import me.devsaki.hentoid.util.image.startsWith
import me.devsaki.hentoid.util.pause
import net.sf.sevenzipjbinding.ArchiveFormat
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.ISeekableStream
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.OptionalInt
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.IntStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Archive / unarchive helper for formats supported by 7Z
 */
const val ZIP_MIME_TYPE = "application/zip"

private val SUPPORTED_EXTENSIONS = setOf("zip", "epub", "cbz", "cbr", "cb7", "7z", "rar")

private val archiveNamesFilter = NameFilter { isArchiveExtensionSupported(getExtension(it)) }

private const val INTERRUPTION_MSG = "Extract archive INTERRUPTED"

// In Java and Kotlin, byte type is signed !
// => Converting all raw values to byte to be sure they are evaluated as expected
private val ZIP_SIGNATURE = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte())
private val SEVEN_ZIP_SIGNATURE = byteArrayOf(
    0x37.toByte(),
    0x7A.toByte(),
    0xBC.toByte(),
    0xAF.toByte(),
    0x27.toByte(),
    0x1C.toByte()
)
private val RAR5_SIGNATURE = byteArrayOf(
    0x52.toByte(),
    0x61.toByte(),
    0x72.toByte(),
    0x21.toByte(),
    0x1A.toByte(),
    0x07.toByte(),
    0x01.toByte(),
    0x00.toByte()
)
private val RAR_SIGNATURE =
    byteArrayOf(0x52.toByte(), 0x61.toByte(), 0x72.toByte(), 0x21.toByte())

private const val BUFFER = 32 * 1024


fun getSupportedExtensions(): Set<String> {
    return SUPPORTED_EXTENSIONS
}

/**
 * Determine if the given file extension is supported by the app as an archive
 *
 * @param extension File extension to test
 * @return True if the app supports the reading of files with the given extension as archives; false if not
 */
private fun isArchiveExtensionSupported(extension: String): Boolean {
    return !SUPPORTED_EXTENSIONS.find { it.equals(extension, ignoreCase = true) }
        .isNullOrEmpty()
}

/**
 * Determine if the given file name is supported by the app as an archive
 *
 * @param fileName File name to test
 * @return True if the app supports the reading of the given file name as an archive; false if not
 */
fun isSupportedArchive(fileName: String): Boolean {
    return isArchiveExtensionSupported(getExtension(fileName))
}

/**
 * Build a [NameFilter] only accepting archive files supported by the app
 *
 * @return [NameFilter] only accepting archive files supported by the app
 */
fun getArchiveNamesFilter(): NameFilter {
    return archiveNamesFilter
}

/**
 * Determine the format of the given binary data if it's an archive
 *
 * @param binary Archive binary header data to determine the format for
 * @return Format of the given binary data; null if not supported
 */
private fun getTypeFromArchiveHeader(binary: ByteArray): ArchiveFormat? {
    if (binary.size < 8) return null

    // In Java, byte type is signed !
    // => Converting all raw values to byte to be sure they are evaluated as expected
    return if (binary.startsWith(ZIP_SIGNATURE)) ArchiveFormat.ZIP
    else if (binary.startsWith(SEVEN_ZIP_SIGNATURE)) ArchiveFormat.SEVEN_ZIP
    else if (binary.startsWith(RAR5_SIGNATURE)) ArchiveFormat.RAR5
    else if (binary.startsWith(RAR_SIGNATURE)) ArchiveFormat.RAR
    else null
}

/**
 * Get the entries of the given archive file
 *
 * @param file    Archive file to read
 * @return List of the entries of the given archive file; an empty list if the archive file is not supported
 * @throws IOException If something horrible happens during I/O
 */
@Throws(IOException::class)
fun Context.getArchiveEntries(file: DocumentFile): List<ArchiveEntry> {
    assertNonUiThread()
    var format: ArchiveFormat?
    getInputStream(this, file).use { fi ->
        val header = ByteArray(8)
        if (fi.read(header) < header.size) return emptyList()
        format = getTypeFromArchiveHeader(header)
    }
    return if (null == format) emptyList() else getArchiveEntries(format!!, file.uri)
}

/**
 * Get the entries of the given archive file
 */
@Throws(IOException::class)
private fun Context.getArchiveEntries(
    format: ArchiveFormat,
    uri: Uri
): List<ArchiveEntry> {
    assertNonUiThread()
    val callback = ArchiveOpenCallback()
    val result = ArrayList<ArchiveEntry>()
    try {
        DocumentFileRandomInStream(this, uri).use { stream ->
            SevenZip.openInArchive(format, stream, callback).use { inArchive ->
                val itemCount = inArchive.numberOfItems
                for (i in 0 until itemCount) {
                    result.add(
                        ArchiveEntry(
                            inArchive.getStringProperty(i, PropID.PATH),
                            inArchive.getStringProperty(i, PropID.SIZE).toLong()
                        )
                    )
                }
            }
        }
    } catch (e: SevenZipException) {
        Timber.w(e)
    }
    return result
}

@Throws(IOException::class)
fun Context.extractArchiveEntriesCached(
    uri: Uri,
    entriesToExtract: List<Pair<String, String>>?,
    interrupt: AtomicBoolean?,
    onExtract: ((String, Uri) -> Unit)? = null,
    onComplete: (() -> Unit)? = null
) {
    return extractArchiveEntries(
        uri,
        fileCreator = { targetFileName -> File(StorageCache.createFile(targetFileName).path!!) },
        fileFinder = { targetFileName -> StorageCache.getFile(targetFileName) },
        entriesToExtract, interrupt, onExtract, onComplete
    )
}

@Throws(IOException::class)
fun Context.extractArchiveEntries(
    uri: Uri,
    targetFolder: File,  // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
    entriesToExtract: List<Pair<String, String>>?,
    interrupt: AtomicBoolean?,
    onExtract: ((String, Uri) -> Unit)? = null,
    onComplete: (() -> Unit)? = null
) {
    return extractArchiveEntries(
        uri,
        fileCreator = { targetFileName -> File(targetFolder.absolutePath + File.separator + targetFileName) },
        fileFinder = { targetFileName -> findFile(targetFolder, targetFileName)?.let { Uri.fromFile(it)} },
        entriesToExtract, interrupt, onExtract, onComplete
    )
}

/**
 * Extract the given archive entries; blocking call
 *
 * @param uri               Uri of the archive file to extract from
 * @param targetFolder      Folder to extract files to
 * @param entriesToExtract  List of entries to extract; null to extract everything
 *      left = relative paths to the archive root
 *      right = internal identifier of the resource to extract (for remapping purposes)
 * @returns Uris of extracted files
 * @throws IOException If something horrible happens during I/O
 */
@Throws(IOException::class)
fun Context.extractArchiveEntriesBlocking(
    uri: Uri,
    targetFolder: File,  // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
    entriesToExtract: List<Pair<String, String>>
): List<Uri> {
    val result = ConcurrentHashMap<Int, Uri>()
    val callback: (String, Uri) -> Unit =
        { id, fileUri ->
            // Use the ID to detect the order of the file that was just extracted
            val indexOpt: OptionalInt = IntStream.range(0, entriesToExtract.size)
                .filter { i -> id == entriesToExtract[i].second }
                .findFirst()
            if (indexOpt.isPresent) result[indexOpt.asInt] = fileUri
        }
    extractArchiveEntries(
        uri,
        fileCreator = { targetFileName -> File(targetFolder.absolutePath + File.separator + targetFileName) },
        fileFinder = { targetFileName -> findFile(targetFolder, targetFileName)?.let { Uri.fromFile(it)} },
        entriesToExtract, null,
        callback, null
    )

    // Block calling thread until all entries are processed
    val delay = 250
    var nbPauses = 0
    var lastSize = 0
    while (result.size < entriesToExtract.size) {
        result.apply {
            if (lastSize == size) {
                // 3 seconds timeout when no progression
                if (nbPauses++ > 3 * 1000 / delay) throw IOException("Extraction timed out")
            } else {
                nbPauses = 0
            }
            lastSize = size
        }
        pause(delay)
    }
    return result.entries.sortedBy { it.key }.map { it.value }
}

/**
 * Extract the given entries from the given archive file
 *
 * @param uri              Uri of the archive file to extract from
 * @param fileCreator      Method to call to create a new file to extract to
 * @param fileFinder       Method to call to find a file in the extraction location
 * @param entriesToExtract List of entries to extract; null to extract everything
 *      left = relative paths to the archive root
 *      right = internal identifier of the resource to extract (for remapping purposes)
 * @throws IOException If something horrible happens during I/O
 */
@Throws(IOException::class)
private fun Context.extractArchiveEntries(
    uri: Uri,
    fileCreator: (String) -> File,
    fileFinder: (String) -> Uri?,
    entriesToExtract: List<Pair<String, String>>?,
    interrupt: AtomicBoolean?,
    onExtract: ((String, Uri) -> Unit)?,
    onComplete: (() -> Unit)?
) {
    assertNonUiThread()
    var format: ArchiveFormat?
    getInputStream(this, uri).use { fi ->
        val header = ByteArray(8)
        if (fi.read(header) < header.size) return
        format = getTypeFromArchiveHeader(header)
    }
    if (null == format) return
    val fileNames: MutableMap<Int, String> = HashMap()
    val identifiers: MutableMap<Int, String> = HashMap()

    // TODO handle the case where the extracted elements would saturate storage space
    try {
        DocumentFileRandomInStream(this, uri).use { stream ->
            SevenZip.openInArchive(format, stream).use { inArchive ->
                val itemCount = inArchive.numberOfItems
                for (archiveIndex in 0 until itemCount) {
                    val fileName = inArchive.getStringProperty(archiveIndex, PropID.PATH)
                    // Selective extraction
                    if (entriesToExtract != null) {
                        for (entry in entriesToExtract) {
                            if (entry.first.equals(fileName, ignoreCase = true)) {
                                // TL;DR - We don't care about folders
                                // If we were coding an all-purpose extractor we would have to create folders
                                // But Hentoid just wants to extract a bunch of files in one single place !
                                fileNames[archiveIndex] = fileName.replace(File.separator, "_")
                                identifiers[archiveIndex] = entry.second
                                break
                            }
                        }
                    } else {
                        fileNames[archiveIndex] = fileName.replace(File.separator, "_")
                    }
                }
                val callback =
                    ArchiveExtractCallback(
                        fileCreator,
                        fileFinder,
                        fileNames,
                        identifiers,
                        interrupt,
                        onExtract,
                        onComplete
                    )
                val indexes = fileNames.keys.toIntArray()
                inArchive.extract(indexes, false, callback)
            }
        }
    } catch (e: SevenZipException) {
        Timber.w(e)
        throw IOException(e)
    }
}

// ================= ZIP FILE CREATION
/**
 * Add the given file to the given ZipOutputStream
 *
 * @param file    File to be added
 * @param stream  ZipOutputStream to write to
 * @param buffer  Buffer to be used
 * @throws IOException If something horrible happens during I/O
 */
@Throws(IOException::class)
private fun Context.addFile(
    file: DocumentFile,
    stream: ZipOutputStream,
    buffer: ByteArray
) {
    Timber.d("Adding: %s", file)
    getInputStream(this, file).use { fi ->
        BufferedInputStream(fi, BUFFER).use { origin ->
            val zipEntry = ZipEntry(file.name)
            stream.putNextEntry(zipEntry)
            var count: Int
            while (origin.read(buffer, 0, BUFFER).also { count = it } != -1) {
                stream.write(buffer, 0, count)
            }
        }
    }
}

/**
 * Archive the given files into the given output stream using the ZIP format
 *
 * @param files   List of the files to be archived
 * @param out     Output stream to write to
 * @throws IOException If something horrible happens during I/O
 */
@Throws(IOException::class)
fun Context.zipFiles(
    files: List<DocumentFile>,
    out: OutputStream,
    isCanceled: () -> Boolean,
    progress: ((Float) -> Unit)? = null
) {
    assertNonUiThread()
    ZipOutputStream(BufferedOutputStream(out)).use { zipOutputStream ->
        val data = ByteArray(BUFFER)
        files.forEachIndexed { index, file ->
            if (isCanceled()) return@forEachIndexed
            addFile(file, zipOutputStream, data)
            // Signal progress every 10 pages
            if (0 == index % 10) progress?.invoke(index * 1f / files.size)
        }
        out.flush()
    }
}

// Describes an entry inside an archive
data class ArchiveEntry(val path: String, val size: Long)

private class ArchiveOpenCallback : IArchiveOpenCallback {
    override fun setTotal(files: Long?, bytes: Long?) {
        Timber.v("Archive open, total work: $files files, $bytes bytes")
    }

    override fun setCompleted(files: Long?, bytes: Long?) {
        Timber.v("Archive open, completed: $files files, $bytes bytes")
    }
}

// https://stackoverflow.com/a/28805474/8374722; https://stackoverflow.com/questions/28897329/documentfile-randomaccessfile
class DocumentFileRandomInStream(context: Context, val uri: Uri) : IInStream {
    private lateinit var contentResolver: ContentResolver
    private var pfdInput: ParcelFileDescriptor? = null
    private var stream: FileInputStream? = null
    private var streamSize: Long = 0
    private var position: Long = 0

    init {
        try {
            contentResolver = context.contentResolver
            openUri()
            stream?.let {
                streamSize = it.channel.size()
            }
        } catch (e: IOException) {
            Timber.e(e)
        }
    }

    @Throws(IOException::class)
    private fun openUri() {
        stream?.close()
        pfdInput?.close()
        pfdInput = contentResolver.openFileDescriptor(uri, "r")
        pfdInput?.let {
            stream = FileInputStream(it.fileDescriptor)
        }
    }

    @Throws(SevenZipException::class)
    override fun seek(offset: Long, seekOrigin: Int): Long {
        var seekDelta: Long = 0
        when (seekOrigin) {
            ISeekableStream.SEEK_CUR -> seekDelta = offset
            ISeekableStream.SEEK_SET -> seekDelta = offset - position
            ISeekableStream.SEEK_END -> seekDelta = streamSize + offset - position
        }
        if (position + seekDelta > streamSize) position = streamSize
        if (seekDelta != 0L) {
            try {
                if (seekDelta < 0) {
                    // "skip" can only go forward, so we have to start over
                    openUri()
                    skipNBytes(position + seekDelta)
                } else {
                    skipNBytes(seekDelta)
                }
            } catch (e: IOException) {
                throw SevenZipException(e)
            }
        }
        position += seekDelta
        //Timber.v("position : $position")
        return position
    }

    // Taken from Java14's InputStream
    // as basic skip is limited by the size of its buffer
    @Throws(IOException::class)
    private fun skipNBytes(toSkip: Long) {
        if (toSkip > 0) {
            var n = toSkip
            val ns = stream!!.skip(n)
            if (ns < n) { // skipped too few bytes
                // adjust number to skip
                n -= ns
                // read until requested number skipped or EOS reached
                while (n > 0 && stream!!.read() != -1) n--
                // if not enough skipped, then EOFE
                if (n != 0L) throw EOFException()
            } else if (ns != n) { // skipped negative or too many bytes
                throw IOException("Unable to skip exactly")
            }
        }
    }

    @Throws(SevenZipException::class)
    override fun read(bytes: ByteArray): Int {
        return try {
            var result = stream!!.read(bytes)
            position += result
            //if (result != bytes.size) Timber.w("diff %s expected; %s read", bytes.size, result)
            if (result < 0) result = 0
            result
        } catch (e: IOException) {
            throw SevenZipException(e)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        stream?.close()
        pfdInput?.close()
    }
}

private class ArchiveExtractCallback(
    private val fileCreator: (String) -> File,
    private val fileFinder: (String) -> Uri?,
    private val fileNames: Map<Int, String>,
    private val identifiers: Map<Int, String>,
    private val interrupt: AtomicBoolean?,
    private val onExtract: ((String, Uri) -> Unit)?,
    private val onComplete: (() -> Unit)?
) : IArchiveExtractCallback {
    private var nbProcessed = 0
    private var extractAskMode: ExtractAskMode? = null
    private var stream: SequentialOutStream? = null

    // Out parameters
    private var identifier = ""
    private var uri: Uri? = null

    @Throws(SevenZipException::class)
    override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
        if (interrupt != null && interrupt.get()) {
            Timber.v(INTERRUPTION_MSG)
            throw SevenZipException(INTERRUPTION_MSG)
        }
        this.extractAskMode = extractAskMode
        if (!fileNames.containsKey(index)) return null

        val fileName: String
        if (identifiers.isNotEmpty()) {
            identifier = identifiers[index] ?: return null
            fileName = identifier
        } else fileName = fileNames[index] ?: ""

        val existing = fileFinder.invoke(fileName)
        Timber.v("Extract archive, get stream: $index to: $extractAskMode as $fileName")
        return try {
            val targetFile: File
            if (null == existing) {
                targetFile = fileCreator.invoke(fileName)
                targetFile.createNewFile()
            } else {
                targetFile = legacyFileFromUri(existing)!!
            }
            uri = Uri.fromFile(targetFile)
            stream = SequentialOutStream(getOutputStream(targetFile))
            stream
        } catch (e: IOException) {
            throw SevenZipException(e)
        }
    }

    @Throws(SevenZipException::class)
    override fun prepareOperation(extractAskMode: ExtractAskMode) {
        Timber.v("Extract archive, prepare to: %s", extractAskMode)
        if (interrupt != null && interrupt.get()) {
            Timber.v(INTERRUPTION_MSG)
            throw SevenZipException(INTERRUPTION_MSG)
        }
    }

    @Throws(SevenZipException::class)
    override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
        Timber.v(
            "Extract archive, %s completed with: %s",
            extractAskMode,
            extractOperationResult
        )
        stream?.close()
        if (extractOperationResult != ExtractOperationResult.OK) {
            throw SevenZipException(extractOperationResult.toString())
        } else {
            uri?.let {
                onExtract?.invoke(identifier, it)
            }
        }
        if (extractAskMode != null && extractAskMode == ExtractAskMode.EXTRACT) {
            nbProcessed++
            if (nbProcessed == fileNames.size) onComplete?.invoke()
        }
    }

    override fun setTotal(total: Long) {
        Timber.v("Extract archive, bytes planned: %s", total)
    }

    override fun setCompleted(complete: Long) {
        Timber.v("Extract archive, bytes processed: %s", complete)
    }
}

private class SequentialOutStream(private val out: OutputStream) : ISequentialOutStream {
    @Throws(SevenZipException::class)
    override fun write(data: ByteArray): Int {
        if (data.isEmpty()) {
            throw SevenZipException("Empty data")
        }
        try {
            out.write(data)
        } catch (e: IOException) {
            throw SevenZipException(e)
        }
        return data.size
    }

    @Throws(IOException::class)
    fun close() {
        out.close()
    }
}