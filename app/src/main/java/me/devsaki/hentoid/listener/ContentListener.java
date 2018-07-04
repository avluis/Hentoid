package me.devsaki.hentoid.listener;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;

public interface ContentListener {
    void onContentReady(List<Content> contentList, int totalContent);

    void onContentFailed();
}

