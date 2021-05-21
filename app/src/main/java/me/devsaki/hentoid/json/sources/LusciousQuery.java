package me.devsaki.hentoid.json.sources;

import java.util.Map;

import me.devsaki.hentoid.util.StringHelper;

@SuppressWarnings("unused, MismatchedQueryAndUpdateOfCollection")
public class LusciousQuery {
    private Map<String, String> variables;

    public String getIdVariable() {
        if (null == variables) return "";
        return StringHelper.protect(variables.get("id"));
    }
}
