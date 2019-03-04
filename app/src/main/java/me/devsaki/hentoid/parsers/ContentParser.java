package me.devsaki.hentoid.parsers;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;

public interface ContentParser {
    List<ImageFile> parseImageList(Content content) throws Exception;
}
