package me.devsaki.hentoid.util.download

import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

data class RequestOrder(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String>,
    val site: Site,
    val targetDir: DocumentFile,
    val fileName: String,
    val pageIndex: Int,
    val backupUrl: String,
    val img: ImageFile
) {
    val killSwitch: AtomicBoolean = AtomicBoolean(false)
    val id: UUID = UUID.randomUUID()
    
    enum class HttpMethod {
        GET, POST, OPTIONS
    }

    enum class NetworkErrorType {
        INTERRUPTED, NETWORK_ERROR, PARSE, FILE_IO
    }

    data class NetworkError(
        val statusCode: Int,
        val message: String,
        val type: NetworkErrorType
    )
}
