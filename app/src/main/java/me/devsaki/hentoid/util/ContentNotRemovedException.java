package me.devsaki.hentoid.util;

import me.devsaki.hentoid.database.domains.Content;

public class ContentNotRemovedException extends Exception {

    private final Content content;

    public ContentNotRemovedException(Content content, String message)
    {
        super(message);
        this.content = content;
    }

    public Content getContent() {
        return content;
    }
}
