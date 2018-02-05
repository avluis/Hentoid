package me.devsaki.hentoid.parsers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.HttpClientHelper;
import timber.log.Timber;

import static me.devsaki.hentoid.enums.Site.NHENTAI;

/**
 * Created by Shiro on 1/5/2016.
 * Handles parsing of content from nhentai
 */
public class NhentaiParser {

    public static Content parseContent(String json) throws JSONException {
        JSONObject jsonContent = new JSONObject(json);
        String title = jsonContent.getJSONObject("title").getString("pretty");
        String url = "/" + jsonContent.getInt("id") + '/';
        int qtyPages = jsonContent.getInt("num_pages");

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

            Tag tag = new Tag();
            JSONObject singleTagObject = allTags.optJSONObject(i);
            if (singleTagObject != null) {
                tag.url = singleTagObject.getString("url");
                tag.type = singleTagObject.getString("type");
                tag.name = singleTagObject.getString("name");
            } else {
                JSONArray singleTagArray = allTags.getJSONArray(i);
                tag.url = singleTagArray.getString(0);
                tag.type = singleTagArray.getString(1);
                tag.name = singleTagArray.getString(2);
            }

            Attribute attribute = new Attribute()
                    .setUrl(tag.url)
                    .setName(tag.name);

            switch (tag.type) {
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
                default: // do nothing
                    break;
            }
            attributes.add(attribute);
        }

        String author = "";
        if (attributes.containsKey(AttributeType.ARTIST) && attributes.get(AttributeType.ARTIST).size() > 0) author = attributes.get(AttributeType.ARTIST).get(0).getName();
        if (author.equals("")) // Try and get Circle
        {
            if (attributes.containsKey(AttributeType.CIRCLE) && attributes.get(AttributeType.CIRCLE).size() > 0) author = attributes.get(AttributeType.CIRCLE).get(0).getName();
        }

        return new Content()
                .setTitle(title)
                .setAuthor(author)
                .setUrl(url)
                .setCoverImageUrl(coverImageUrl)
                .setAttributes(attributes)
                .setQtyPages(qtyPages)
                .setStatus(StatusContent.SAVED)
                .setSite(NHENTAI);
    }

    public static List<String> parseImageList(Content content) {
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

    private static class Tag {
        String url;
        String name;
        String type;
    }
}
