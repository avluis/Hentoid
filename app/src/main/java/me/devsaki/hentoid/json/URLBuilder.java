package me.devsaki.hentoid.json;

import me.devsaki.hentoid.workers.PrimaryImportWorker;

/**
 * General builder for URLs
 *
 * @deprecated Replaced by {@link PrimaryImportWorker} methods; class is kept for retrocompatibilty
 */
@SuppressWarnings("DeprecatedIsStillUsed") // Shouldn't be used for new devs but still used to ensure retrocompatiblity with old files
@Deprecated
public class URLBuilder {

    private String url;
    private String description;

    public String getId() {
        int idxStart = url.lastIndexOf('/');
        String id = url.substring(idxStart);
        String category = url.replace(id, "");
        category = category.substring(category.lastIndexOf('/'));

        return category + id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
