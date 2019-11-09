package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.database.domains.Content;

interface WebContentListener {
    void onResultReady(Content results, boolean downloadImmediately);

    void onResultFailed();
}
