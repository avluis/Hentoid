package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;

public class DummyParser implements ImageListParser {
    @Override
    public List<ImageFile> parseImageList(@NonNull Content content) {
        return (null == content.getImageFiles()) ? new ArrayList<>() : new ArrayList<>(content.getImageFiles());
    }

    @Override
    public ImmutablePair<String, Optional<String>> parseImagePage(@NonNull InputStream pageData, @NonNull String baseUri) {
        throw new NotImplementedException();
    }

    @Override
    public Optional<ImageFile> parseBackupUrl(@NonNull String url, @NonNull Map<String, String> requestHeaders, int order, int maxPages, Chapter chapter) {
        return Optional.of(ParseHelper.urlToImageFile(url, order, maxPages, StatusContent.SAVED, chapter));
    }
}
