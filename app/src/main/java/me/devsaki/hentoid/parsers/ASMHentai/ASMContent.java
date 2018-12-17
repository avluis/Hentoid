package me.devsaki.hentoid.parsers.ASMHentai;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import pl.droidsonroids.jspoon.annotation.Selector;

public class ASMContent {
    @Selector(value = "head [rel=canonical]", attr="href")
    private String pageUrl;
    @Selector(value = "div.cover a", attr="href")
    private String url;
    @Selector(value = "div.cover a img", attr="src")
    private String img;
    @Selector("div.info h1:first-child")
    private String title;
    @Selector("div.pages h3")
    private List<String> pages;


    private String getProtocol()
    {
        return pageUrl.startsWith("https") ? "https" : "http";
    }

    public Content toContent()
    {
        Content result = new Content();

        result.setSite(pageUrl.toLowerCase().contains("comics") ? Site.ASMHENTAI_COMICS : Site.ASMHENTAI);
        result.setUrl(url.substring(0, url.length() - 2).replace("/gallery", ""));
        result.setCoverImageUrl(getProtocol() + "://"+img);
        result.setTitle(title);
        result.setQtyPages(Integer.parseInt(pages.get(0).replace("Pages: ", "")));
        // TODO attributes

        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }
}
