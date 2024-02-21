package me.devsaki.hentoid.json.core

import com.squareup.moshi.Json

data class JsonLangSettings(
    val languages: List<JsonLanguage>
) {
    data class JsonLanguage(
        @Json(name = "lang_code")
        val langCode: String,
        @Json(name = "flag_country_code")
        val flagCountryCode: String,
        @Json(name = "local_name")
        val localName: String,
        @Json(name = "english_name")
        val englishName: String
    )
}
