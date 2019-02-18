package me.devsaki.hentoid.parsers;

import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;

public class DummyParser implements ContentParser {
    @Override
    public List<ImageFile> parseImageList(Content content) {
        return Collections.emptyList();
    }
}
