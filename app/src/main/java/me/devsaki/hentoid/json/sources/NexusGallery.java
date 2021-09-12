package me.devsaki.hentoid.json.sources;

import com.squareup.moshi.Json;

import java.util.List;

@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection","squid:S1172"})
public class NexusGallery {
    private String b;
    private String r;
    private String i;
    @Json(name = "pages")
    private List<String> pages;

    public List<String> toUrls() {
        return pages;
    }
}
