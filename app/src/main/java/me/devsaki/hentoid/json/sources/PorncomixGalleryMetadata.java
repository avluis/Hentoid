package me.devsaki.hentoid.json.sources;

import com.squareup.moshi.Json;

import java.util.List;

@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class PorncomixGalleryMetadata {
    private @Json(name = "@graph")
    List<GraphData> graph;

    private static class GraphData {
        private @Json(name = "@type")
        String type;
        private String datePublished;
    }

    public String getDatePublished() {
        if (graph != null) {
            for (GraphData data : graph) {
                if (data.type != null && data.type.toLowerCase().equals("webpage")) {
                    return data.datePublished;
                }
            }
        }
        return "";
    }
}
