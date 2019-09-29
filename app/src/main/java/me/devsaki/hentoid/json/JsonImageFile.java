package me.devsaki.hentoid.json;

import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;

public class JsonImageFile {

    public Integer order;
    public String url;
    public String name;
    public boolean favourite;
    public StatusContent status;

    private JsonImageFile() {}

    static JsonImageFile fromEntity(ImageFile f) {
        JsonImageFile result = new JsonImageFile();
        result.order = f.getOrder();
        result.url = f.getUrl();
        result.name = f.getName();
        result.status = f.getStatus();
        result.favourite = f.isFavourite();
        return result;
    }

    ImageFile toEntity() {
        ImageFile result = new ImageFile(order, url, status);
        result.setName(name);
        result.setFavourite(favourite);
        return result;
    }
}
