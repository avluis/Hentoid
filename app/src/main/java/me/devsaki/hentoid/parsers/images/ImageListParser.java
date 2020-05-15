package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;

public interface ImageListParser {
    List<ImageFile> parseImageList(@NonNull Content content) throws Exception;

    Optional<ImageFile> parseBackupUrl(@NonNull String url, int order, int maxPages) throws Exception;
}
