package me.devsaki.hentoid.parsers;

import java.util.List;

import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;

public interface ImageListParser {
    List<ImageFile> parseImageList(Content content) throws Exception;

    @Nullable
    ImageFile parseBackupUrl(String url, int order) throws Exception;
}
