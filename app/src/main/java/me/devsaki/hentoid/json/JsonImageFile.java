package me.devsaki.hentoid.json;

import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;

public class JsonImageFile {

    public Integer order;
    public String url;
    public String name;
    public boolean favourite;
    public StatusContent status;

    JsonImageFile(ImageFile f) {
        this.order = f.getOrder();
        this.url = f.getUrl();
        this.name = f.getName();
        this.status = f.getStatus();
        this.favourite = f.isFavourite();
    }

    ImageFile toEntity() {
        ImageFile result = new ImageFile(order, url, status);
        result.setName(name);
        result.setFavourite(favourite);
        return result;
    }
}
