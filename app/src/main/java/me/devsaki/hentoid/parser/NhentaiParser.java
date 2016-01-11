package me.devsaki.hentoid.parser;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.jar.Attributes;

import me.devsaki.hentoid.CustomMultiMap;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.Site;

/**
 * Created by Shiro on 1/5/2016.
 */
public class NhentaiParser {

    private static final String TAG = NhentaiParser.class.getName();

    public static Content parseContent(String json) throws JSONException {
        JSONObject jsonContent = new JSONObject(json);
        String titleTEMP = jsonContent.getJSONObject("title").getString("english");
        String urlTEMP = "/" + jsonContent.getInt("id");
        int qtyPagesTEMP = jsonContent.getInt("num_pages");

        String mediaId = jsonContent.getString("media_id");
        String extension = jsonContent.getJSONObject("images").getJSONObject("cover").getString("t");
        switch (extension) {
            case "j": extension = "jpg"; break;
            case "p": extension = "png"; break;
            default: extension = "gif"; break;
        }
        String coverImageUrl = "http://t.nhentai.net/galleries/" + mediaId + "/cover." + extension;

        JSONArray allTags = jsonContent.getJSONArray("tags");
        CustomMultiMap attributes = new CustomMultiMap();
        for(int i = 0; i < allTags.length(); i++) {

            JSONArray singleTag = allTags.getJSONArray(i);
            String urlIdStr = singleTag.getString(0);
            String tagTypeStr = singleTag.getString(1);
            String nameStr = singleTag.getString(2);

            AttributeType attrType;
            switch (tagTypeStr) {
                case "artist": attrType = AttributeType.ARTIST; break;
                case "character": attrType = AttributeType.CHARACTER; break;
                case "parody": attrType = AttributeType.SERIE; break;
                case "language": attrType = AttributeType.LANGUAGE; break;
                case "tag": attrType = AttributeType.TAG; break;
                case "group": attrType = AttributeType.CIRCLE; break;
                case "category": attrType = AttributeType.CATEGORY; break;
                default: attrType = null; break;
            }

            Attribute attribute = new Attribute();
            attribute.setType(attrType);
            attribute.setName(nameStr);
            attribute.setUrl(urlIdStr);
            attributes.add(attrType, attribute);
        }

        Content result = new Content(
                titleTEMP,
                urlTEMP,
                coverImageUrl,
                attributes,
                qtyPagesTEMP,
                Site.NHENTAI
        );
        result.setImageFiles(new ArrayList<ImageFile>(qtyPagesTEMP));
        return result;
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
                    case "j": extension = ".jpg"; break;
                    case "p": extension = ".png"; break;
                    default: extension = ".gif"; break;
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