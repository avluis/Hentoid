package me.devsaki.hentoid.activities.sources;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.database.domains.Content;

/**
 * Callback interface for web content parsers
 */
interface WebContentListener {
    /**
     * Callback when the page has been successfuly parsed into a Content
     * @param results Parsed Content
     * @param quickDownload True if the action has been triggered by a quick download
     */
    void onResultReady(@NonNull Content results, boolean quickDownload);

    /**
     * Callback when the page should have been parsed into a Content, but the parsing failed
     */
    void onResultFailed();
}
