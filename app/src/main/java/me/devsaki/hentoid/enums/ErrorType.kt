package me.devsaki.hentoid.enums

import androidx.annotation.StringRes
import io.objectbox.converter.PropertyConverter
import me.devsaki.hentoid.R

enum class ErrorType(val code: Int, @param:StringRes val displayName: Int, val engName: String) {
    PARSING(0, R.string.errortype_parsing, "Parsing"),
    NETWORKING(1, R.string.errortype_networking, "Networking"),
    IO(2, R.string.errortype_io, "I/O"),
    CAPTCHA(3, R.string.errortype_captcha, "Captcha"),
    IMG_PROCESSING(4, R.string.errortype_img_processing, "Image processing"),
    SITE_LIMIT(5, R.string.errortype_site_limit, "Downloads/bandwidth limit reached"),
    ACCOUNT(6, R.string.errortype_account, "No account or insufficient credentials"),
    IMPORT(7, R.string.errortype_import, "No local file found after import"),
    WIFI(8, R.string.errortype_wifi, "Book skipped because of Wi-Fi download size limitations"),
    BLOCKED(9, R.string.errortype_blocked, "Book contains a blocked tag"),
    UNDEFINED(99, R.string.errortype_undefined, "Undefined");

    class ErrorTypeConverter : PropertyConverter<ErrorType, Int> {
        override fun convertToEntityProperty(databaseValue: Int?): ErrorType? {
            if (databaseValue == null) {
                return null
            }
            for (type in entries) {
                if (type.code == databaseValue) {
                    return type
                }
            }
            return UNDEFINED
        }

        override fun convertToDatabaseValue(entityProperty: ErrorType): Int {
            return entityProperty.code
        }
    }

    companion object {
        fun searchByCode(code: Int): ErrorType {
            for (entry in entries) {
                if (entry.code == code) return entry
            }
            return UNDEFINED
        }
    }
}