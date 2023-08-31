package me.devsaki.hentoid.util.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.util.Pair
import androidx.documentfile.provider.DocumentFile
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import me.devsaki.hentoid.util.Helper
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Archive / unarchive helper for formats supported by 7Z
 */
object ArchiveHelper {
    const val ZIP_MIME_TYPE = "application/zip"

    private val SUPPORTED_EXTENSIONS = setOf("zip", "epub", "cbz", "cbr", "cb7", "7z", "rar")

    private val archiveNamesFilter =
        FileHelper.NameFilter { displayName: String ->
            isArchiveExtensionSupported(
                FileHelper.getExtension(displayName)
            )
        }
    private const val CACHE_SEPARATOR = "£"

    private const val INTERRUPTION_MSG = "Extract archive INTERRUPTED"

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
        return !SUPPORTED_EXTENSIONS.find { e -> e.equals(extension, ignoreCase = true) }
            .isNullOrEmpty()
    }

    /**
     * Determine if the given file name is supported by the app as an archive
     *
     * @param fileName File name to test
     * @return True if the app supports the reading of the given file name as an archive; false if not
     */
    fun isSupportedArchive(fileName: String): Boolean {
        return isArchiveExtensionSupported(FileHelper.getExtension(fileName))
    }

    /**
     * Build a [FileHelper.NameFilter] only accepting archive files supported by the app
     *
     * @return [FileHelper.NameFilter] only accepting archive files supported by the app
     */
    fun getArchiveNamesFilter(): FileHelper.NameFilter {
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
        return if (0x50.toByte() == binary[0] && 0x4B.toByte() == binary[1] && 0x03.toByte() == binary[2]) ArchiveFormat.ZIP
        else if (0x37.toByte() == binary[0] && 0x7A.toByte() == binary[1] && 0xBC.toByte() == binary[2] && 0xAF.toByte() == binary[3] && 0x27.toByte() == binary[4] && 0x1C.toByte() == binary[5]) ArchiveFormat.SEVEN_ZIP
        else if (0x52.toByte() == binary[0] && 0x61.toByte() == binary[1] && 0x72.toByte() == binary[2] && 0x21.toByte() == binary[3] && 0x1A.toByte() == binary[4] && 0x07.toByte() == binary[5] && 0x01.toByte() == binary[6] && 0x00.toByte() == binary[7]) ArchiveFormat.RAR5
        else if (0x52.toByte() == binary[0] && 0x61.toByte() == binary[1] && 0x72.toByte() == binary[2] && 0x21.toByte() == binary[3]) ArchiveFormat.RAR
        else null
    }

    /**
     * Get the entries of the given archive file
     *
     * @param context Context to be used
     * @param file    Archive file to read
     * @return List of the entries of the given archive file; an empty list if the archive file is not supported
     * @throws IOException If something horrible happens during I/O
     */
    @Throws(IOException::class)
    fun getArchiveEntries(context: Context, file: DocumentFile): List<ArchiveEntry> {
        Helper.assertNonUiThread()
        var format: ArchiveFormat?
        FileHelper.getInputStream(context, file).use { fi ->
            val header = ByteArray(8)
            if (fi.read(header) < header.size) return emptyList()
            format = getTypeFromArchiveHeader(header)
        }
        return if (null == format) emptyList() else getArchiveEntries(context, format!!, file.uri)
    }

    /**
     * Get the entries of the given archive file
     */
    @Throws(IOException::class)
    private fun getArchiveEntries(
        context: Context,
        format: ArchiveFormat,
        uri: Uri
    ): List<ArchiveEntry> {
        Helper.assertNonUiThread()
        val callback = ArchiveOpenCallback()
        val result = ArrayList<ArchiveEntry>()
        try {
            DocumentFileRandomInStream(context, uri).use { stream ->
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

    /**
     * Extract the given entries from the given archive file
     * This is the variant to be used with RxJava
     *
     * @param context          Context to be used
     * @param file             Archive file to extract from
     * @param targetFolder     Target folder to create the archives into
     * @param entriesToExtract List of entries to extract (left = relative paths to the archive root / right = names of the target files - set to blank to keep original name); null to extract everything
     * @return Observable that follows the extraction of each entry
     * @throws IOException If something horrible happens during I/O
     */
    @Throws(IOException::class)
    fun extractArchiveEntriesRx(
        context: Context,
        file: DocumentFile,
        targetFolder: File,  // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
        entriesToExtract: List<Pair<String, String>>?,
        interrupt: AtomicBoolean?
    ): Observable<Uri?>? {
        Helper.assertNonUiThread()
        return if (entriesToExtract != null && entriesToExtract.isEmpty()) Observable.empty() else Observable.create { emitter: ObservableEmitter<Uri>? ->
            extractArchiveEntries(
                context,
                file.uri,
                targetFolder,
                entriesToExtract,
                interrupt,
                emitter
            )
        }
    }

    @Throws(IOException::class)
    fun extractArchiveEntriesCached(
        context: Context,
        uri: Uri,
        entriesToExtract: List<Pair<String, String>>?,
        interrupt: AtomicBoolean?,
        emitter: ObservableEmitter<Uri>?
    ) {
        return extractArchiveEntries(
            context, uri,
            fileCreator = { targetFileName -> File(DiskCache.createFile(targetFileName).path!!) },
            fileFinder = { targetFileName -> DiskCache.getFile(targetFileName) },
            entriesToExtract, interrupt, emitter
        )
    }

    @Throws(IOException::class)
    fun extractArchiveEntries(
        context: Context,
        uri: Uri,
        targetFolder: File,  // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
        entriesToExtract: List<Pair<String, String>>?,
        interrupt: AtomicBoolean?,
        emitter: ObservableEmitter<Uri>?
    ) {
        return extractArchiveEntries(
            context, uri,
            fileCreator = { targetFileName -> File(targetFolder.absolutePath + File.separator + targetFileName) },
            fileFinder = { targetFileName -> findFile(targetFolder, targetFileName) },
            entriesToExtract, interrupt, emitter
        )
    }

    /**
     * Extract the given entries from the given archive file
     *
     * @param context          Context to be used
     * @param uri              Uri of the archive file to extract from
     * @param fileCreator      Method to call to create a new file to extract to
     * @param fileFinder       Method to call to find a file in the extraction location
     * @param entriesToExtract List of entries to extract (left = relative paths to the archive root / right = names of the target files - set to blank to keep original name); null to extract everything
     * @param emitter          Optional emitter to be used when the method is used with RxJava
     * @throws IOException If something horrible happens during I/O
     */
    @Throws(IOException::class)
    private fun extractArchiveEntries(
        context: Context,
        uri: Uri,
        fileCreator: (String) -> File,
        fileFinder: (String) -> Uri?,
        entriesToExtract: List<Pair<String, String>>?,
        interrupt: AtomicBoolean?,
        emitter: ObservableEmitter<Uri>?
    ) {
        Helper.assertNonUiThread()
        var format: ArchiveFormat?
        FileHelper.getInputStream(context, uri).use { fi ->
            val header = ByteArray(8)
            if (fi.read(header) < header.size) return
            format = getTypeFromArchiveHeader(header)
        }
        if (null == format) return
        val fileNames: MutableMap<Int, String> = HashMap()

        // TODO handle the case where the extracted elements would saturate disk space
        try {
            DocumentFileRandomInStream(context, uri).use { stream ->
                SevenZip.openInArchive(format, stream).use { inArchive ->
                    val itemCount = inArchive.numberOfItems
                    for (archiveIndex in 0 until itemCount) {
                        var fileName =
                            inArchive.getStringProperty(archiveIndex, PropID.PATH)
                        if (entriesToExtract != null) {
                            for (entry in entriesToExtract) {
                                if (entry.first.equals(fileName, ignoreCase = true)) {
                                    // TL;DR - We don't care about folders
                                    // If we were coding an all-purpose extractor we would have to create folders
                                    // But Hentoid just wants to extract a bunch of files in one single place !
                                    if (entry.second.isEmpty()) {
                                        val lastSeparator =
                                            fileName.lastIndexOf(File.separator)
                                        if (lastSeparator > -1) fileName =
                                            fileName.substring(lastSeparator + 1)
                                    } else {
                                        fileName =
                                            entry.second + "." + FileHelper.getExtension(
                                                fileName
                                            )
                                    }
                                    fileNames[archiveIndex] = fileName
                                    break
                                }
                            }
                        } else {
                            val lastSeparator = fileName.lastIndexOf(File.separator)
                            if (lastSeparator > -1) fileName = fileName.substring(lastSeparator + 1)
                            fileNames[archiveIndex] = fileName
                        }
                    }
                    val callback =
                        ArchiveExtractCallback(
                            fileCreator,
                            fileFinder,
                            fileNames,
                            interrupt,
                            emitter
                        )
                    val indexes =
                        Helper.getPrimitiveArrayFromSet(fileNames.keys)
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
     * @param context Context to be used
     * @param file    File to be added
     * @param stream  ZipOutputStream to write to
     * @param buffer  Buffer to be used
     * @throws IOException If something horrible happens during I/O
     */
    @Throws(IOException::class)
    private fun addFile(
        context: Context,
        file: DocumentFile,
        stream: ZipOutputStream,
        buffer: ByteArray
    ) {
        Timber.d("Adding: %s", file)
        FileHelper.getInputStream(context, file).use { fi ->
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
     * @param context Context to be used
     * @param files   List of the files to be archived
     * @param out     Output stream to write to
     * @throws IOException If something horrible happens during I/O
     */
    @Throws(IOException::class)
    fun zipFiles(
        context: Context,
        files: List<DocumentFile>,
        out: OutputStream,
        progress: ((Float) -> Unit)? = null
    ) {
        Helper.assertNonUiThread()
        ZipOutputStream(BufferedOutputStream(out)).use { zipOutputStream ->
            val data = ByteArray(BUFFER)
            files.forEachIndexed { index, file ->
                addFile(context, file, zipOutputStream, data)
                progress?.invoke(index * 1f / files.size)
            }
            out.flush()
        }
    }

    /**
     * Format the output file name to facilitate "flat" unarchival in one single folder
     * NB : We do not want to overwrite files with the same name if they are located in different folders within the archive
     *
     * @param index    Index of the archived file
     * @param fileName Name of the archived file (name only, without the path)
     * @return Output file name of the given archived file
     */
    fun formatCacheFileName(index: Int, fileName: String): String {
        return index.toString() + CACHE_SEPARATOR + fileName
    }

    // This is a dumb struct class, nothing more
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
            Timber.d("position : $position")
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
                if (result != bytes.size) Timber.w("diff %s expected; %s read", bytes.size, result)
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

    private fun findFile(targetFolder: File, targetName: String): Uri? {
        val files = targetFolder.listFiles { _, name: String ->
            name.equals(
                targetName,
                ignoreCase = true
            )
        }
        return if (null == files || files.isEmpty()) null
        else Uri.fromFile(files[0])
    }

    private class ArchiveExtractCallback(
        private val fileCreator: (String) -> File,
        private val fileFinder: (String) -> Uri?,
        private val fileNames: Map<Int, String>,
        private val interrupt: AtomicBoolean?,
        private val emitter: ObservableEmitter<Uri>?
    ) : IArchiveExtractCallback {
        private var nbProcessed = 0
        private var extractAskMode: ExtractAskMode? = null
        private var stream: SequentialOutStream? = null
        private var uri: Uri? = null

        @Throws(SevenZipException::class)
        override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
            Timber.v("Extract archive, get stream: $index to: $extractAskMode")
            if (interrupt != null && interrupt.get()) {
                Timber.v(INTERRUPTION_MSG)
                throw SevenZipException(INTERRUPTION_MSG)
            }
            this.extractAskMode = extractAskMode
            val fileName = fileNames[index] ?: return null
            val targetFileName = formatCacheFileName(index, fileName)
            val existing = fileFinder.invoke(targetFileName)
            return try {
                val targetFile: File
                if (null == existing) {
                    targetFile = fileCreator.invoke(targetFileName)
                    if (!targetFile.createNewFile()) throw IOException("Could not create file " + targetFile.path)
                } else {
                    targetFile = FileHelper.legacyFileFromUri(existing)!!
                }
                uri = Uri.fromFile(targetFile)
                stream = SequentialOutStream(FileHelper.getOutputStream(targetFile))
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
                    emitter?.onNext(it)
                }
            }
            if (extractAskMode != null && extractAskMode == ExtractAskMode.EXTRACT) {
                nbProcessed++
                if (nbProcessed == fileNames.size) emitter?.onComplete()
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
}