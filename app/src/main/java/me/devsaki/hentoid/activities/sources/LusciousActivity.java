package me.devsaki.hentoid.activities.sources;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.sources.LusciousQueryParam;
import me.devsaki.hentoid.retrofit.sources.LusciousServer;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

public class LusciousActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "luscious.net";
    private static final String[] GALLERY_FILTER = {"operationName=AlbumGet", "luscious.net/[A-Za-z0-9\\-]+/[A-Za-z0-9\\-_]+_[0-9]+/$"};

    Site getStartSite() {
        return Site.LUSCIOUS;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new LusciousWebClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class LusciousWebClient extends CustomWebViewClient {

        LusciousWebClient(String[] filter, WebContentListener listener) {
            super(filter, listener);
        }

        // Call the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean downloadImmediately) {
            String bookId;

            if (urlStr.contains(GALLERY_FILTER[0])) { // Triggered by a graphQL request
                String vars = Uri.parse(urlStr).getQueryParameter("variables");
                if (null == vars || vars.isEmpty()) {
                    Timber.w("No variable field found in %s", urlStr);
                    return null;
                }

                try {
                    bookId = JsonHelper.jsonToObject(vars, LusciousQueryParam.class).getId();
                } catch (Exception e) {
                    Timber.w(e);
                    return null;
                }
            } else { // Triggered by the loading of the page itself
                // ID is the last numeric part of the URL
                // e.g. /albums/lewd_title_ch_1_3_42116/ -> 42116 is the ID
                int lastIndex = urlStr.lastIndexOf('_');
                bookId = urlStr.substring(lastIndex + 1, urlStr.length() - 1);
            }

            Map<String, String> query = new HashMap<>();
            query.put("id", (int) (Math.random() * 10) + "");
            query.put("operationName", "AlbumGet");
            query.put("query", " query AlbumGet($id: ID!) { album { get(id: $id) { ... on Album { ...AlbumStandard } ... on MutationError { errors { code message } } } } } fragment AlbumStandard on Album { __typename id title labels description created modified like_status number_of_favorites rating status marked_for_deletion marked_for_processing number_of_pictures number_of_animated_pictures slug is_manga url download_url permissions cover { width height size url } created_by { id url name display_name user_title avatar { url size } } content { id title url } language { id title url } tags { id category text url count } genres { id title slug url } audiences { id title url url } last_viewed_picture { id position url } } "); // Yeah...
            query.put("variables", "{\"id\":\"" + bookId + "\"}");

            compositeDisposable.add(LusciousServer.API.getBookMetadata(query)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            metadata ->
                                    getPages(metadata.toContent(), bookId, 1, downloadImmediately, new ArrayList<>()),
                            throwable -> {
                                Timber.e(throwable, "Error parsing content.");
                                isHtmlLoaded = true;
                                listener.onResultFailed();
                            })
            );
            return null;
        }

        // TODO move that method in an ImageListParser
        private void getPages(@NonNull Content content, @NonNull String bookId, int pageNumber, boolean downloadImmediately, @NonNull List<ImageFile> imageFiles) {
            Map<String, String> query = new HashMap<>();
            query.put("id", (int) (Math.random() * 10) + "");
            query.put("operationName", "AlbumListOwnPictures");
            query.put("query", " query AlbumListOwnPictures($input: PictureListInput!) { picture { list(input: $input) { info { ...FacetCollectionInfo } items { ...PictureStandardWithoutAlbum } } } } fragment FacetCollectionInfo on FacetCollectionInfo { page has_next_page has_previous_page total_items total_pages items_per_page url_complete url_filters_only } fragment PictureStandardWithoutAlbum on Picture { __typename id title created like_status number_of_comments number_of_favorites status width height resolution aspect_ratio url_to_original url_to_video is_animated position tags { id category text url } permissions url thumbnails { width height size url } } "); // Yeah...
            query.put("variables", "{\"input\":{\"filters\":[{\"name\":\"album_id\",\"value\":\"" + bookId + "\"}],\"display\":\"position\",\"page\":" + pageNumber + "}}");

            compositeDisposable.add(LusciousServer.API.getGalleryMetadata(query)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            metadata ->
                            {
                                imageFiles.addAll(metadata.toImageFileList(imageFiles.size()));
                                if (metadata.getNbPages() > pageNumber) {
                                    getPages(content, bookId, pageNumber + 1, downloadImmediately, imageFiles);
                                } else {
                                    content.setImageFiles(imageFiles);
                                    isHtmlLoaded = true;
                                    listener.onResultReady(content, downloadImmediately);
                                }
                            },
                            throwable -> {
                                Timber.e(throwable, "Error parsing content.");
                                isHtmlLoaded = true;
                                listener.onResultFailed();
                            })
            );
        }
    }
}
