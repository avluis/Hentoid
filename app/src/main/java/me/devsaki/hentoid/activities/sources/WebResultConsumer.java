package me.devsaki.hentoid.activities.sources;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.database.domains.Content;

public interface WebResultConsumer {
    /**
     * Callback when the page has been successfuly parsed into a Content
     *
     * @param result        Parsed Content
     * @param quickDownload True if the action has been triggered by a quick download action
     */
    void onResultReady(@NonNull Content result, boolean quickDownload);

    /**
     * Callback when the page does not have any Content to parse
     */
    void onNoResult();

    /**
     * Callback when the page should have been parsed into a Content, but the parsing failed
     */
    void onResultFailed();
}
