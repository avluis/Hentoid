package me.devsaki.hentoid.parsers.ASMHentai;

import me.devsaki.hentoid.database.domains.Content;
import pl.droidsonroids.jspoon.annotation.Selector;

public class ASMContent {
    @Selector(value = "div.cover a", attr="href")
    String url;


    public Content toContent()
    {
        Content result = new Content();

        result.setUrl(url.substring(0, url.length() - 2).replace("/gallery", ""));

        return result;
    }
}
