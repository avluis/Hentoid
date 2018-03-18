package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import java.util.List;

import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

/**
 * Created by DevSaki on 09/05/2015.
 * Content builder (legacy: kept to support older library)
 */
@Deprecated
public class ContentV1 {

    @Expose
    private String url;
    @Expose
    private String title;
    @Expose
    private String htmlDescription;
    @Expose
    private Attribute serie;
    @Expose
    private List<Attribute> artists;
    @Expose
    private List<Attribute> publishers;
    @Expose
    private Attribute language;
    @Expose
    private List<Attribute> tags;
    @Expose
    private List<Attribute> translators;
    @Expose
    private String coverImageUrl;
    @Expose
    private Integer qtyPages;
    @Expose
    private long uploadDate;
    @Expose
    private Attribute user;
    @Expose
    private long downloadDate;
    @Expose
    private StatusContent status;
    @Expose
    private List<ImageFile> imageFiles;
    @Expose
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

    private Site getSite() {
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
        attributes.add(artists);
        attributes.add(publishers);
        attributes.add(translators);
        attributes.add(tags);
        if (serie != null) attributes.add(serie);
        if (language != null) attributes.add(language);
        if (user != null) attributes.add(user);

        String author = "";
        if (attributes.containsKey(AttributeType.ARTIST) && attributes.get(AttributeType.ARTIST).size() > 0)
            author = attributes.get(AttributeType.ARTIST).get(0).getName();
        if (author.equals("")) // Try and get Circle
        {
            if (attributes.containsKey(AttributeType.CIRCLE) && attributes.get(AttributeType.CIRCLE).size() > 0)
                author = attributes.get(AttributeType.CIRCLE).get(0).getName();
        }

        return new Content()
                .setSite(getSite())
                .setUrl(url)
                .setUploadDate(uploadDate)
                .setAttributes(attributes)
                .setImageFiles(imageFiles)
                .setCoverImageUrl(coverImageUrl)
                .setTitle(title)
                .setAuthor(author)
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
