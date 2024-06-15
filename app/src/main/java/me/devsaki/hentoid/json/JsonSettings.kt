package me.devsaki.hentoid.json;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class JsonSettings {

    private Map<String, Object> settings = new HashMap<>();

    public JsonSettings() {
        // Nothing special do to here
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(@NonNull Map<String, Object> settings) {
        this.settings = settings;
    }
}
