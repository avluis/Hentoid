package me.devsaki.hentoid.json.sources;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;

@SuppressWarnings("unused, MismatchedQueryAndUpdateOfCollection")
public class LusciousGalleryMetadata {
    private PictureData data;

    private static class PictureData {
        private PictureInfoContainer picture;
    }

    private static class PictureInfoContainer {
        private PictureInfo list;
    }

    private static class PictureInfo {
        private PictureContainerMetadata info;
        private List<PictureMetadata> items;
    }

    private static class PictureContainerMetadata {
        private int total_pages;
    }

    private static class PictureMetadata {
        private String url_to_original;
    }

    public int getNbPages() {
        return data.picture.list.info.total_pages;
    }

    public List<ImageFile> toImageFileList() {
        return toImageFileList(0);
    }

    public List<ImageFile> toImageFileList(int offset) {
        List<ImageFile> result = new ArrayList<>();

        int order = offset;
        List<PictureMetadata> imageList = data.picture.list.items;
        for (PictureMetadata pm : imageList)
            result.add(new ImageFile(++order, pm.url_to_original, StatusContent.SAVED, imageList.size()));

        return result;
    }
}
