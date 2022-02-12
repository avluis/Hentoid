package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.LusciousGalleryMetadata;
import me.devsaki.hentoid.retrofit.sources.LusciousServer;
import me.devsaki.hentoid.util.Helper;
import retrofit2.Response;
import timber.log.Timber;

public class LusciousParser extends BaseImageListParser {

    public List<ImageFile> parseImageListImpl(@NonNull Content onlineContent, @Nullable Content storedContent) throws Exception {
        List<ImageFile> result = new ArrayList<>();

        result.add(ImageFile.newCover(onlineContent.getCoverImageUrl(), StatusContent.SAVED));
        getPages(onlineContent, onlineContent.getUniqueSiteId(), 1, result);

        progressComplete();

        return result;
    }

    private void getPages(@NonNull Content content, @NonNull String bookId, int pageNumber, @NonNull List<ImageFile> imageFiles) {
        Map<String, String> query = new HashMap<>();
        query.put("id", Helper.getRandomInt(10) + "");
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
                imageFiles.addAll(metadata.toImageFileList(imageFiles.size() - 1)); // Don't count cover in the offset
                if (metadata.getNbPages() > pageNumber) {
                    progressStart(content, null, metadata.getNbPages());
                    progressPlus();
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

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        /// We won't use that as parseImageListImpl is overriden directly
        return null;
    }
}
