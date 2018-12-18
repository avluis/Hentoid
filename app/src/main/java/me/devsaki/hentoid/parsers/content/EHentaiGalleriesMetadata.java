package me.devsaki.hentoid.parsers.content;

import com.google.gson.annotations.Expose;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;

public class EHentaiGalleriesMetadata {
    @Expose
    public List<EHentaiGalleryMetadata> gmetadata;

    public Content toContent()
    {
        return (gmetadata != null && gmetadata.size() > 0) ? gmetadata.get(0).toContent() : new Content();
    }

}
