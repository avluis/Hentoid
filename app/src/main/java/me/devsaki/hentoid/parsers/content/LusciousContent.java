package me.devsaki.hentoid.parsers.content;

import static me.devsaki.hentoid.activities.sources.LusciousActivity.GALLERY_FILTER;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.LusciousBookMetadata;
import me.devsaki.hentoid.json.sources.LusciousQueryParam;
import me.devsaki.hentoid.retrofit.sources.LusciousServer;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.StringHelper;
import timber.log.Timber;

public class LusciousContent extends BaseContentParser {

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        String bookId;

        if (url.contains(GALLERY_FILTER[0])) { // Triggered by a graphQL request
            String vars = Uri.parse(url).getQueryParameter("variables");
            if (null == vars || vars.isEmpty()) {
                Timber.w("No variable field found in %s", url);
                return null;
            }

            try {
                bookId = JsonHelper.jsonToObject(vars, LusciousQueryParam.class).getId();
            } catch (Exception e) {
                Timber.w(e);
                return null;
            }
        } else if (StringHelper.isNumeric(url)) { // Book ID is directly provided
            bookId = url;
        } else { // Triggered by the loading of the page itself
            // ID is the last numeric part of the URL
            // e.g. /albums/lewd_title_ch_1_3_42116/ -> 42116 is the ID
            int lastIndex = url.lastIndexOf('_');
            bookId = url.substring(lastIndex + 1, url.length() - 1);
        }

        Map<String, String> query = new HashMap<>();
        query.put("id", new Random().nextInt(10) + "");
        query.put("operationName", "AlbumGet");
        query.put("query", " query AlbumGet($id: ID!) { album { get(id: $id) { ... on Album { ...AlbumStandard } ... on MutationError { errors { code message } } } } } fragment AlbumStandard on Album { __typename id title labels description created modified like_status number_of_favorites rating status marked_for_deletion marked_for_processing number_of_pictures number_of_animated_pictures slug is_manga url download_url permissions cover { width height size url } created_by { id url name display_name user_title avatar { url size } } content { id title url } language { id title url } tags { id category text url count } genres { id title slug url } audiences { id title url url } last_viewed_picture { id position url } } "); // Yeah...
        query.put("variables", "{\"id\":\"" + bookId + "\"}");

        try {
            LusciousBookMetadata metadata = LusciousServer.API.getBookMetadata(query).execute().body();
            if (metadata != null) return metadata.update(content, updateImages);
        } catch (IOException e) {
            Timber.e(e, "Error parsing content.");
        }
        return new Content().setSite(Site.LUSCIOUS).setStatus(StatusContent.IGNORED);
    }
}
