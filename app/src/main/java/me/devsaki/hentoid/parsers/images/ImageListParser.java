package me.devsaki.hentoid.parsers.images;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;

public interface ImageListParser {
    List<ImageFile> parseImageList(@NonNull Content content) throws Exception;

    List<ImageFile> parseImageList(@NonNull Content onlineContent, @NonNull Content storedContent) throws Exception;

    ImmutablePair<String, Optional<String>> parseImagePage(@NonNull String url, @NonNull List<Pair<String, String>> requestHeaders) throws IOException, LimitReachedException, EmptyResultException;

    Optional<ImageFile> parseBackupUrl(
            @NonNull String url,
            @NonNull Map<String, String> requestHeaders,
            int order,
            int maxPages,
            Chapter chapter) throws Exception;
}
