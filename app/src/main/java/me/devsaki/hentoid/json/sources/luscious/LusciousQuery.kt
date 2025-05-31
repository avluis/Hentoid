package me.devsaki.hentoid.json.sources.luscious

data class LusciousQuery(
    val variables: Map<String, String>?
) {
    fun getIdVariable(): String {
        return if (null == variables) "" else variables["id"] ?: ""
    }
}