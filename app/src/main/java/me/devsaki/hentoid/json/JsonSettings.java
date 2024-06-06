package me.devsaki.hentoid.json;

import androidx.annotation.NonNull;

import org.apache.commons.collections4.map.HashedMap;
import java.util.Map;

public class JsonSettings {

    private Map<String, Object> settings = new HashedMap<>();

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
