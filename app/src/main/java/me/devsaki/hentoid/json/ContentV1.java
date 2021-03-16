package me.devsaki.hentoid.json;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.database.domains.AttributeMap;

/**
 * Created by DevSaki on 09/05/2015.
 * Content builder (legacy: kept to support older library)
 *
 * @deprecated Replaced by {@link Content}; class is kept for retrocompatibilty
 */
@SuppressWarnings("DeprecatedIsStillUsed") // Shouldn't be used for new devs but still used to ensure retrocompatiblity with old files
@Deprecated
public class ContentV1 {

    private String url;
    private String title;
    private String htmlDescription;
    private Attribute serie;
    private List<Attribute> artists;
    private List<Attribute> publishers;
    private Attribute language;
    private List<Attribute> tags;
    private List<Attribute> translators;
    private String coverImageUrl;
    private Integer qtyPages;
    private long uploadDate;
    private Attribute user;
    private long downloadDate;
    private StatusContent status;
    private List<ImageFile> imageFiles;
    private Site site;

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSeries(Attribute serie) {
        this.serie = serie;
    }

    public void setArtists(List<Attribute> artists) {
        this.artists = artists;
    }

    public void setLanguage(Attribute language) {
        this.language = language;
    }

    public void setTags(List<Attribute> tags) {
        this.tags = tags;
    }

    public void setTranslators(List<Attribute> translators) {
        this.translators = translators;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public void setQtyPages(Integer qtyPages) {
        this.qtyPages = qtyPages;
    }

    public void setDownloadDate(long downloadDate) {
        this.downloadDate = downloadDate;
    }

    public StatusContent getStatus() {
        return status;
    }

    public void setMigratedStatus() {
        status = StatusContent.MIGRATED;
    }

    public Site getSite() {
        // to keep compatibility, if null return FAKKU
        if (site == null) {
            return Site.FAKKU;
        }

        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public Content toV2Content() {
        AttributeMap attributes = new AttributeMap();
        attributes.addAll(artists);
        attributes.addAll(publishers);
        attributes.addAll(translators);
        attributes.addAll(tags);
        if (serie != null) attributes.add(serie);
        if (language != null) attributes.add(language);
        if (user != null) attributes.add(user);

        return new Content()
                .setSite(getSite())
                .setUrl(url)
                .setUploadDate(uploadDate)
                .addAttributes(attributes)
                .setImageFiles(imageFiles)
                .setCoverImageUrl(coverImageUrl)
                .setTitle(title)
                .populateAuthor()
                .setQtyPages(qtyPages)
                .setDownloadDate(downloadDate)
                .setStatus(status);
    }

    public String getHtmlDescription() {
        return htmlDescription;
    }

    public void setHtmlDescription(String htmlDescription) {
        this.htmlDescription = htmlDescription;
    }
}
