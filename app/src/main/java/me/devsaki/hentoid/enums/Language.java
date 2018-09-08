package me.devsaki.hentoid.enums;

import javax.annotation.Nullable;

import timber.log.Timber;

public enum Language {

    ANY(0, "Any language", "*"),
    INDONESIAN(1, "Indonesian", "ind"),
    CATALAN(2, "Catalan", "cat"),
    CZECH(3, "Czech", "ces"),
    DANISH(4, "Danish", "dan"),
    GERMAN(5, "German", "deu"),
    ESTONIAN(6, "Estonian", "est"),
    ENGLISH(7, "English", "eng"),
    SPANISH(8, "Spanish", "spa"),
    ESPERANTO(9, "Esperanto", "epo"),
    FRENCH(10, "French", "fra"),
    ITALIAN(11, "Italian", "ita"),
    LATIN(12, "Latin", "lat"),
    HUNGARIAN(13, "Hungarian", "hun"),
    DUTCH(14, "Dutch", "nld"),
    NORWEGIAN(15, "Norwegian", "nor"),
    POLISH(16, "Polish", "pol"),
    PORTUGUESE(17, "Portuguese", "por"),
    ROMANIAN(18, "Romanian", "ron"),
    ALBANIAN(19, "Albanian", "sqi"),
    SLOVAK(20, "Slovak", "slk"),
    FINNISH(21, "Finnish", "fin"),
    SWEDISH(22, "Swedish", "swe"),
    TAGALOG(23, "Tagalog", "tgl"),
    VIETNAMESE(24, "Vietnamese", "vie"),
    TURKISH(25, "Turkish", "tur"),
    GREEK(26, "Greek", "ell"),
    RUSSIAN(27, "Russian", "rus"),
    UKRAINIAN(28, "Ukrainian", "ukr"),
    HEBREW(29, "Hebrew", "heb"),
    ARABIC(30, "Arabic", "ara"),
    THAI(31, "Thai", "tha"),
    KOREAN(32, "Korean", "kor"),
    CHINESE(33, "Chinese", "zho"),
    JAPANESE(34, "Japanese", "jpn");


    private final int code;         // Mikan codes
    private final String name;
    private final String isoCode;   // ISO 639-3

    Language(int code, String name, String isoCode) {
        this.code = code;
        this.name = name;
        this.isoCode = isoCode;
    }

    @Nullable
    public static Language searchByCode(int code) {
        if (code == -1) {
            Timber.w("Invalid site code!");
        }
        for (Language l : Language.values()) {
            if (l.getCode() == code)
                return l;
        }
        return null;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getIsoCode() {
        return isoCode;
    }
}
