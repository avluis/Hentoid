package me.devsaki.hentoid.parsers;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;

public interface ContentParser {
    List<String> parseImageList(Content content);
}
