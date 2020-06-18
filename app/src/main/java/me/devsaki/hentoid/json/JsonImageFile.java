package me.devsaki.hentoid.json;

import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;

class JsonImageFile {

    private Integer order;
    private String url;
    private String name;
    private boolean isCover;
    private boolean favourite;
    private StatusContent status;

    private JsonImageFile() {}

    static JsonImageFile fromEntity(ImageFile f) {
        JsonImageFile result = new JsonImageFile();
        result.order = f.getOrder();
        result.url = f.getUrl();
        result.name = f.getName();
        result.status = f.getStatus();
        result.isCover = f.isCover();
        result.favourite = f.isFavourite();
        return result;
    }

    ImageFile toEntity(int maxPages) {
        ImageFile result = new ImageFile(order, url, status, maxPages);
        result.setName(name);
        result.setIsCover(isCover);
        result.setFavourite(favourite);
        return result;
    }
}
