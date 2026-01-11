package me.devsaki.hentoid.util.file

import me.devsaki.hentoid.core.CHARSET_LATIN_1
import me.devsaki.hentoid.util.startsWith

const val MIME_TYPE_PDF = "application/pdf"

private val pdfNamesFilter = NameFilter { getExtension(it).equals("pdf", true) }

private val PDF_SIGNATURE = "%PDF-".toByteArray(CHARSET_LATIN_1)

/**
 * Build a [NameFilter] only accepting PDF files supported by the app
 *
 * @return [NameFilter] only accepting PDF files supported by the app
 */
fun getPdfNamesFilter(): NameFilter {
    return pdfNamesFilter
}

/**
 * Determine the format of the given binary data if it's an archive
 *
 * @param binary Archive binary header data to determine the format for
 * @return Format of the given binary data; null if not supported
 */
private fun isPdfFromHeader(binary: ByteArray): Boolean {
    if (binary.size < 5) return false

    // In Java, byte type is signed !
    // => Converting all raw values to byte to be sure they are evaluated as expected
    return binary.startsWith(PDF_SIGNATURE)
}
