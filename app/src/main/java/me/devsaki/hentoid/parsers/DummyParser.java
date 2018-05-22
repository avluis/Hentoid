package me.devsaki.hentoid.parsers;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;

public class DummyParser implements ContentParser {
    @Override
    public Content parseContent(String urlString) {
        return null;
    }

    @Override
    public List<String> parseImageList(Content content) {
        return null;
    }
}
