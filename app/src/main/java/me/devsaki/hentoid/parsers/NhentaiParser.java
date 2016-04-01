package me.devsaki.hentoid.parsers;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

/**
 * Created by Shiro on 1/5/2016.
 * Handles parsing of content from nhentai
 */
public class NhentaiParser {
    private static final String TAG = NhentaiParser.class.getName();

    public static Content parseContent(String json) throws JSONException {
        JSONObject jsonContent = new JSONObject(json);
        String titleTEMP = jsonContent.getJSONObject("title").getString("english");
        String urlTEMP = "/" + jsonContent.getInt("id") + '/';
        int qtyPagesTEMP = jsonContent.getInt("num_pages");

        String mediaId = jsonContent.getString("media_id");
        String extension = jsonContent.getJSONObject("images").getJSONObject("cover").getString("t");
        switch (extension) {
            case "j":
                extension = "jpg";
                break;
            case "p":
                extension = "png";
                break;
            default:
                extension = "gif";
                break;
        }
        String coverImageUrl = "http://t.nhentai.net/galleries/" + mediaId + "/cover." + extension;

        JSONArray allTags = jsonContent.getJSONArray("tags");
        AttributeMap attributes = new AttributeMap();
        for (int i = 0; i < allTags.length(); i++) {

            JSONArray singleTag = allTags.getJSONArray(i);
            Attribute attribute = new Attribute()
                    .setUrl(singleTag.getString(0))
                    .setName(singleTag.getString(2));

            switch (singleTag.getString(1)) {
                case "artist":
                    attribute.setType(AttributeType.ARTIST);
                    break;
                case "character":
                    attribute.setType(AttributeType.CHARACTER);
                    break;
                case "parody":
                    attribute.setType(AttributeType.SERIE);
                    break;
                case "language":
                    attribute.setType(AttributeType.LANGUAGE);
                    break;
                case "tag":
                    attribute.setType(AttributeType.TAG);
                    break;
                case "group":
                    attribute.setType(AttributeType.CIRCLE);
                    break;
                case "category":
                    attribute.setType(AttributeType.CATEGORY);
                    break;
            }
            attributes.add(attribute);
        }

        return new Content()
                .setTitle(titleTEMP)
                .setUrl(urlTEMP)
                .setCoverImageUrl(coverImageUrl)
                .setAttributes(attributes)
                .setQtyPages(qtyPagesTEMP)
                .setStatus(StatusContent.SAVED)
                .setSite(Site.NHENTAI);
    }

    public static List<String> parseImageList(String json) {
        List<String> imagesUrl = new ArrayList<>();

        try {
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
                imagesUrl.add(urlImage);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing content", e);
        }

        return imagesUrl;
    }
}