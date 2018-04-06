package me.devsaki.hentoid.model;

/**
 * General builder for URLs
 */
@Deprecated
public class URLBuilder {

    private String url;
    private String description;

    public String getId() {
        int idxStart = url.lastIndexOf("/");
        String id = url.substring(idxStart);
        String category = url.replace(id, "");
        category = category.substring(category.lastIndexOf("/"));

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
