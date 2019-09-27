package me.devsaki.hentoid.parsers;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;

public class DummyParser implements ImageListParser {
    @Override
    public List<ImageFile> parseImageList(Content content) {
        return content.getImageFiles();
    }

    @Override
    public ImageFile parseBackupUrl(String url, int order) {
        return ParseHelper.urlToImageFile(url, order);
    }
}
