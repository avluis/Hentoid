package me.devsaki.hentoid.enums;

import androidx.annotation.StringRes;

import io.objectbox.converter.PropertyConverter;
import me.devsaki.hentoid.R;

public enum ErrorType {

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

    private final int code;
    private final int name; // For UI display
    private final String engName; // For logging

    ErrorType(int code, int name, String engName) {
        this.code = code;
        this.name = name;
        this.engName = engName;
    }

    public static ErrorType searchByCode(int code) {
        for (ErrorType entry : ErrorType.values()) {
            if (entry.getCode() == code)
                return entry;
        }
        return UNDEFINED;
    }

    private int getCode() {
        return code;
    }

    public @StringRes
    int getName() {
        return name;
    }

    public String getEngName() {
        return engName;
    }

    public static class ErrorTypeConverter implements PropertyConverter<ErrorType, Integer> {
        @Override
        public ErrorType convertToEntityProperty(Integer databaseValue) {
            if (databaseValue == null) {
                return null;
            }
            for (ErrorType type : ErrorType.values()) {
                if (type.getCode() == databaseValue) {
                    return type;
                }
            }
            return ErrorType.UNDEFINED;
        }

        @Override
        public Integer convertToDatabaseValue(ErrorType entityProperty) {
            return entityProperty == null ? null : entityProperty.getCode();
        }
    }
}
