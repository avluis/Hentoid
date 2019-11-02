package me.devsaki.hentoid.json.sources;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;

public class EHentaiGalleriesMetadata {
    private List<EHentaiGalleryMetadata> gmetadata;

    public Content toContent(@Nonnull String url) {
        return (gmetadata != null && !gmetadata.isEmpty()) ? gmetadata.get(0).toContent(url) : new Content();
    }

}
