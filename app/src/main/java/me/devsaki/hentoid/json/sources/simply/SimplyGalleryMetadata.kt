package me.devsaki.hentoid.json.sources.simply

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.json.sources.simply.SimplyContentMetadata.SimplyPageData

@JsonClass(generateAdapter = true)
data class SimplyGalleryMetadata(
    val props: Props? = null
) {
    fun getPageUrls(): List<String> {
        val result: MutableList<String> = ArrayList()
        if (props?.pageProps?.data?.pages == null) return result
        val pages = props.pageProps.data.pages.sortedBy { it.pageNum }

        for (page in pages) {
            val url = page.getFullUrl()
            if (url.isNotEmpty()) result.add(url)
        }

        return result
    }
}

@JsonClass(generateAdapter = true)
data class Props(
    val pageProps: PageProps? = null
)

@JsonClass(generateAdapter = true)
data class PageProps(
    val data: PagesData? = null
)

@JsonClass(generateAdapter = true)
data class PagesData(
    val pages: List<SimplyPageData>? = null
)