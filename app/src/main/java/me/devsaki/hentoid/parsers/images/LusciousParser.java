package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.LusciousGalleryMetadata;
import me.devsaki.hentoid.retrofit.sources.LusciousServer;
import retrofit2.Response;
import timber.log.Timber;

public class LusciousParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();

    public List<ImageFile> parseImageList(@NonNull Content content) {
        List<ImageFile> result = new ArrayList<>();

        result.add(ImageFile.newCover(content.getCoverImageUrl(), StatusContent.SAVED));
        getPages(content, content.getUniqueSiteId(), 1, result);

        progress.complete();

        return result;
    }

    private void getPages(@NonNull Content content, @NonNull String bookId, int pageNumber, @NonNull List<ImageFile> imageFiles) {
        Map<String, String> query = new HashMap<>();
        query.put("id", new Random().nextInt(10) + "");
        query.put("operationName", "AlbumListOwnPictures");
        query.put("query", " query AlbumListOwnPictures($input: PictureListInput!) { picture { list(input: $input) { info { ...FacetCollectionInfo } items { ...PictureStandardWithoutAlbum } } } } fragment FacetCollectionInfo on FacetCollectionInfo { page has_next_page has_previous_page total_items total_pages items_per_page url_complete url_filters_only } fragment PictureStandardWithoutAlbum on Picture { __typename id title created like_status number_of_comments number_of_favorites status width height resolution aspect_ratio url_to_original url_to_video is_animated position tags { id category text url } permissions url thumbnails { width height size url } } "); // Yeah...
        query.put("variables", "{\"input\":{\"filters\":[{\"name\":\"album_id\",\"value\":\"" + bookId + "\"}],\"display\":\"position\",\"page\":" + pageNumber + "}}");

        try {
            Response<LusciousGalleryMetadata> response = LusciousServer.API.getGalleryMetadata(query).execute();
            if (response.isSuccessful()) {
                LusciousGalleryMetadata metadata = response.body();
                if (null == metadata) {
                    Timber.e("No metadata found @ ID %s", bookId);
                    return;
                }
                imageFiles.addAll(metadata.toImageFileList(imageFiles.size()));
                if (metadata.getNbPages() > pageNumber) {
                    if (!progress.hasStarted())
                        progress.start(content.getId(), metadata.getNbPages());
                    progress.advance();
                    getPages(content, bookId, pageNumber + 1, imageFiles);
                } else {
                    content.setImageFiles(imageFiles);
                }
            } else {
                int httpCode = response.code();
                String errorMsg = (response.errorBody() != null) ? response.errorBody().toString() : "";
                Timber.e("Request unsuccessful (HTTP code %s) : %s", httpCode, errorMsg);
            }
        } catch (IOException e) {
            Timber.e(e);
        }
    }

    public Optional<ImageFile> parseBackupUrl(@NonNull String url, @NonNull Map<String, String> requestHeaders, int order, int maxPages, Chapter chapter) {
        // This class does not use backup URLs
        ImageFile img = ImageFile.fromImageUrl(order, url, StatusContent.SAVED, maxPages);
        if (chapter != null) img.setChapter(chapter);
        return Optional.of(img);
    }

    @Override
    public ImmutablePair<String, Optional<String>> parseImagePage(@NonNull InputStream pageData, @NonNull String baseUri) {
        throw new NotImplementedException();
    }
}
