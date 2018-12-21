package me.devsaki.hentoid.parsers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.HttpClientHelper;
import timber.log.Timber;

/**
 * Created by Shiro on 1/5/2016.
 * Handles parsing of content from nhentai
 */
public class NhentaiParser implements ContentParser {

    public List<String> parseImageList(Content content) {
        String url = content.getGalleryUrl();
        url = url.replace("/g", "/api/gallery");
        url = url.substring(0, url.length() - 1);
        List<String> imgUrls = new ArrayList<>();

        try {
            String json = HttpClientHelper.call(url);
            JSONObject gallery = new JSONObject(json);
            String mediaId = gallery.getString("media_id");
            JSONArray images = gallery.getJSONObject("images").getJSONArray("pages");
            String serverUrl = "http://i.nhentai.net/galleries/" + mediaId + "/";

            for (int i = 0; i < images.length(); i++) {
                JSONObject image = images.getJSONObject(i);
                String extension = image.getString("t");
                switch (extension) {
                    case "j":
                        extension = ".jpg";
                        break;
                    case "p":
                        extension = ".png";
                        break;
                    default:
                        extension = ".gif";
                        break;
                }
                String urlImage = serverUrl + (i + 1) + extension;
                imgUrls.add(urlImage);
            }
        } catch (JSONException e) {
            Timber.e(e, "Error parsing content");
        } catch (Exception e) {
            Timber.e(e, "Couldn't connect to resource");
        }
        Timber.d("%s", imgUrls);

        return imgUrls;
    }
}
