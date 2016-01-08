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

    public static Content parseContent(String html) {
        Content result = null;
        Document doc = Jsoup.parse(html);
        Elements elements = doc.select("div#bigcontainer");

        if (elements.size() > 0) {
            String titleTEMP = elements.select("div#info").select("h1").text();
            String urlTEMP = elements.select("div#cover").select("a").attr("href");
            urlTEMP = urlTEMP.substring(2, urlTEMP.lastIndexOf("1/"));
            String coverImageUrlTEMP = elements.select("div#cover").select("img").attr("src");
            Integer qtyPagesTEMP = doc.select("a.gallerythumb").size();

            HashMap<AttributeType, List<Attribute>> attributesTEMP = new HashMap<>();

            Elements baseElements = elements.select("div#info");
            Elements artistsElements = baseElements.select("div.field-name:containsOwn(Artists:)").select("a.tag");
            attributesTEMP.put(AttributeType.ARTIST, parseAttributes(artistsElements, AttributeType.ARTIST));
            Elements characterElements = baseElements.select("div.field-name:containsOwn(Characters:)").select("a.tag");
            attributesTEMP.put(AttributeType.CHARACTER, parseAttributes(characterElements, AttributeType.CHARACTER));
            Elements serieElements = baseElements.select("div.field-name:containsOwn(Parodies:)").select("a.tag");
            attributesTEMP.put(AttributeType.SERIE, parseAttributes(serieElements, AttributeType.SERIE));
            Elements languageElements = baseElements.select("div.field-name:containsOwn(Language:)").select("a.tag");
            attributesTEMP.put(AttributeType.LANGUAGE, parseAttributes(languageElements, AttributeType.LANGUAGE));
            Elements tagElements = baseElements.select("div.field-name:containsOwn(Tags:)").select("a.tag");
            attributesTEMP.put(AttributeType.TAG, parseAttributes(tagElements, AttributeType.TAG));
            Elements circleElements = baseElements.select("div.field-name:containsOwn(Groups:)").select("a.tag");
            attributesTEMP.put(AttributeType.CIRCLE, parseAttributes(circleElements, AttributeType.CIRCLE));
            Elements categoryElements = baseElements.select("div.field-name:containsOwn(Category:)").select("a.tag");
            attributesTEMP.put(AttributeType.CATEGORY, parseAttributes(categoryElements, AttributeType.CATEGORY));

            result = new Content(
                    titleTEMP,
                    urlTEMP,
                    coverImageUrlTEMP,
                    attributesTEMP,
                    qtyPagesTEMP,
                    Site.NHENTAI
            );

            result.setImageFiles(new ArrayList<ImageFile>(qtyPagesTEMP));

        }
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

    private static List<Attribute> parseAttributes(Elements elements, AttributeType attributeType) {
        List<Attribute> attributes = new ArrayList<>(elements.size());
        for (Element element : elements) {
            Attribute attribute = new Attribute();
            attribute.setType(attributeType);
            attribute.setUrl(element.attr("href"));
            attribute.setName(element.ownText());
            attributes.add(attribute);
        }
        return attributes;
    }
}