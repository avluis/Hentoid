package me.devsaki.hentoid.json.core;

import java.util.List;

public class JsonLangSettings {

    public List<JsonLanguage> languages;

    public static class JsonLanguage {
        public String lang_code = null;
        public String flag_country_code = null;
        public String local_name = null;
        public String english_name = null;
    }
}
