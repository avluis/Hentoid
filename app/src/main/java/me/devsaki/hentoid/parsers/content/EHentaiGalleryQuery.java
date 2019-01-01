package me.devsaki.hentoid.parsers.content;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;

public class EHentaiGalleryQuery {
    @Expose
    public String method = "gdata";
    @Expose
    public List<List<String>> gidlist;
    @Expose
    public String namespace = "1";

    public EHentaiGalleryQuery(String galleryId, String galleryKey)
    {
        gidlist = new ArrayList<>();
        List<String> galleryIds = new ArrayList<>();
        galleryIds.add(galleryId);
        galleryIds.add(galleryKey);
        gidlist.add(galleryIds);
    }
}
