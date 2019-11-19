package me.devsaki.hentoid.parsers.content;

import java.util.ArrayList;
import java.util.List;

public class EHentaiGalleryQuery {
    public String method = "gdata";
    public final List<List<String>> gidlist;
    public String namespace = "1";

    public EHentaiGalleryQuery(String galleryId, String galleryKey) {
        gidlist = new ArrayList<>();
        List<String> galleryIds = new ArrayList<>();
        galleryIds.add(galleryId);
        galleryIds.add(galleryKey);
        gidlist.add(galleryIds);
    }
}
