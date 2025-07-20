package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.database.domains.Content

interface WebResultConsumer {
    /**
     * Callback when the page has been successfuly parsed into a Content
     *
     * @param content       Parsed Content
     * @param quickDownload True if the action has been triggered by a quick download action
     */
    fun onContentReady(content: Content, quickDownload: Boolean)

    /**
     * Callback when the page does not have any Content to parse
     */
    fun onNoResult()

    /**
     * Callback when the page should have been parsed into a Content, but the parsing failed
     */
    fun onResultFailed()
}