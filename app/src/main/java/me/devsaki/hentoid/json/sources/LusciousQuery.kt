package me.devsaki.hentoid.json.sources

data class LusciousQuery(
    val variables: Map<String, String>?
) {
    fun getIdVariable(): String {
        return if (null == variables) "" else variables["id"] ?: ""
    }
}