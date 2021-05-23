package me.devsaki.hentoid.json.sources;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused, MismatchedQueryAndUpdateOfCollection")
public class EHentaiGalleryQuery {
    private String method = "gdata";
    private final List<List<String>> gidlist;
    private String namespace = "1";

    public EHentaiGalleryQuery(String galleryId, String galleryKey) {
        gidlist = new ArrayList<>();
        List<String> galleryIds = new ArrayList<>();
        galleryIds.add(galleryId);
        galleryIds.add(galleryKey);
        gidlist.add(galleryIds);
    }
}
