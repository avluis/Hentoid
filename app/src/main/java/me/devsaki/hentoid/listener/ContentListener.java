package me.devsaki.hentoid.listener;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;

public interface ContentListener {
    void onContentReady(List<Content> results, long totalSelectedContent, long totalContent);

    void onContentFailed(Content content, String message);
}

