package me.devsaki.hentoid.json;

import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;

class JsonImageFile {

    private Integer order;
    private String url;
    private String name;
    private boolean isCover;
    private boolean favourite;
    private boolean isRead;
    private StatusContent status;
    private String mimeType;
    private long pHash;

    private JsonImageFile() {
    }

    static JsonImageFile fromEntity(ImageFile f) {
        JsonImageFile result = new JsonImageFile();
        result.order = f.getOrder();
        result.url = f.getUrl();
        result.name = f.getName();
        result.status = f.getStatus();
        result.isCover = f.isCover();
        result.favourite = f.isFavourite();
        result.isRead = f.isRead();
        result.mimeType = f.getMimeType();
        result.pHash = f.getImageHash();
        return result;
    }

    ImageFile toEntity(int maxPages) {
        ImageFile result = new ImageFile(order, url, status, maxPages);
        result.setName(name);
        result.setIsCover(isCover);
        result.setFavourite(favourite);
        result.setRead(isRead);
        result.setMimeType(mimeType);
        result.setImageHash(pHash);
        return result;
    }
}
