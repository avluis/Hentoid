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
 * Determine if the given file name is supported by the app as a PDF
 *
 * @param fileName File name to test
 * @return True if the app supports the reading of the given file name as a PDF; false if not
 */
fun isSupportedPdf(fileName: String): Boolean {
    return getExtension(fileName).equals("pdf", true)
}

/**
 * Determine the format of the given binary data if it's a PDF
 *
 * @param binary PDF binary header data to determine the format for
 * @return True if it's a PDF
 */
fun isPdfFromHeader(binary: ByteArray): Boolean {
    if (binary.size < 5) return false

    // In Java, byte type is signed !
    // => Converting all raw values to byte to be sure they are evaluated as expected
    return binary.startsWith(PDF_SIGNATURE)
}
