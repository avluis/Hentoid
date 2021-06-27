package me.devsaki.hentoid.json;

import me.devsaki.hentoid.database.domains.Chapter;

class JsonChapter {

    private Integer order;
    private String url;
    private String name;

    private JsonChapter() {
    }

    static JsonChapter fromEntity(Chapter c) {
        JsonChapter result = new JsonChapter();
        result.order = c.getOrder();
        result.url = c.getUrl();
        result.name = c.getName();
        return result;
    }

    Chapter toEntity() {
        return new Chapter(order, url, name);
    }
}
