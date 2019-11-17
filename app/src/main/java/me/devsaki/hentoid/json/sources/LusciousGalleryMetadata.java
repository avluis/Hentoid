package me.devsaki.hentoid.json.sources;

import com.squareup.moshi.Json;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;

public class LusciousGalleryMetadata {
    @Json(name = "AlbumListOwnPictures")
    private AlbumListOwnPictures pictures;

    private static class AlbumListOwnPictures {
        private PictureData data;
    }

    private static class PictureData {
        private PictureInfoContainer picture;
    }

    private static class PictureInfoContainer {
        private PictureInfo list;
    }

    private static class PictureInfo {
        //        private PictureContainerMetadata info;
        private List<PictureMetadata> items;
    }

    private static class PictureMetadata {
        private String url_to_original;
    }

    public List<ImageFile> toImageFileList() {
        List<ImageFile> result = new ArrayList<>();

        int order = 0;
        List<PictureMetadata> imageList = pictures.data.picture.list.items;
        for (PictureMetadata pm : imageList)
            result.add(new ImageFile(order++, pm.url_to_original, StatusContent.SAVED, imageList.size()));

        return result;
    }
}
