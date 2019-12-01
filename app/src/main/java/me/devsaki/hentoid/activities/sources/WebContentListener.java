package me.devsaki.hentoid.activities.sources;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.database.domains.Content;

interface WebContentListener {
    void onResultReady(@NonNull Content results, boolean quickDownload);

    void onResultFailed();
}
