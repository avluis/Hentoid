package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;

public interface ImageListParser {
    List<ImageFile> parseImageList(@NonNull Content content) throws Exception;

    Optional<ImageFile> parseBackupUrl(
            @NonNull String url,
            @NonNull Map<String, String> requestHeaders,
            int order,
            int maxPages,
            Chapter chapter) throws Exception;
}
