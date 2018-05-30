package me.devsaki.hentoid.parsers;

import java.io.IOException;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;

public interface ContentParser {
    Content parseContent(String urlString) throws Exception;
    List<String> parseImageList(Content content);
}
