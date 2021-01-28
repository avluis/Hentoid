package me.devsaki.hentoid.json;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class JsonSettings {

    private Map<String, ?> settings = new HashMap<>();

    public JsonSettings() {
        // Nothing special do to here
    }

    public Map<String, ?> getSettings() {
        return settings;
    }

    public void setSettings(@NonNull Map<String, ?> settings) {
        this.settings = settings;
    }
}
