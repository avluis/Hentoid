package me.devsaki.hentoid.parser;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.database.enums.StatusContent;

/**
 * Created by DevSaki on 09/05/2015.
 */
public class FakkuParser {

    private final static String TAG = FakkuParser.class.getName();

    public static List<Content> parseListContents(String html) {
        Document doc = Jsoup.parse(html);
        Elements contentRow = doc.select(".content-row");
        List<Content> result = null;
        if (contentRow.size() > 0) {
            result = new ArrayList<>(contentRow.size());
            for (Element content : contentRow) {
                result.add(parseContent(content));
            }
        }
        return result;
    }

    public static Content parseContent(String html) {
        Content result = null;
        Document doc = Jsoup.parse(html);
        Elements content = doc.select(".content-wrap").select(".row");
        Elements rows = content.select(".row");
        if (content.size() > 0) {
            result = new Content();
            result.setCoverImageUrl("http:" + content.select(".cover").attr("src"));
            Element title = doc.select(".breadcrumbs").select("a").get(1);
            result.setUrl(title.attr("href"));
            result.setTitle(title.text());

            int rowIndex = 1;

            result.setAttributes(new HashMap<AttributeType, List<Attribute>>());

            //series
            result.getAttributes().put(AttributeType.SERIE, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.SERIE));
            //Artist
            result.getAttributes().put(AttributeType.ARTIST, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.ARTIST));

            if (rows.get(rowIndex).select("div.left").html().equals("Event")) {
                rowIndex++;
            }
            if (rows.get(rowIndex).select("div.left").html().equals("Magazine")) {
                rowIndex++;
            }
            //Publisher or Translator
            if (rows.get(rowIndex).select("div.left").html().equals("Publisher")) {
                result.getAttributes().put(AttributeType.PUBLISHER, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.PUBLISHER));
            } else if (rows.get(rowIndex).select("div.left").html().equals("Translator")) {
                result.getAttributes().put(AttributeType.TRANSLATOR, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.TRANSLATOR));
            }
            //Language
            result.getAttributes().put(AttributeType.LANGUAGE, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.LANGUAGE));
            //Pages
            result.setQtyPages(Integer.parseInt(rows.get(rowIndex++).select(".right").html().replace(" pages", "")));
            //Favorites
            if (rows.get(rowIndex).select("div.left").html().equals("Favorites")) {
                result.setQtyFavorites(Integer.parseInt(rows.get(rowIndex++).select(".right").html().replace(" favorites", "").replace(",", "")));
            }
            //Uploader
            Element uploader = rows.get(rowIndex++).select(".right").first();

            Elements user = uploader.select("a");

            if (user.size() > 0) {
                result.getAttributes().put(AttributeType.UPLOADER, new ArrayList<Attribute>(1));

                result.getAttributes().get(AttributeType.UPLOADER).add(new Attribute());
                result.getAttributes().get(AttributeType.UPLOADER).get(0).setUrl(user.first().attr("href"));
                result.getAttributes().get(AttributeType.UPLOADER).get(0).setName(user.first().html());
                result.getAttributes().get(AttributeType.UPLOADER).get(0).setType(AttributeType.UPLOADER);
            }
            String date = uploader.html().substring(uploader.html().lastIndexOf(" on ") + 4).trim();
            date = date.replace("st,", ",").replace("nd,", ",").replace("rd,", ",").replace("th,", ",");

            SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
            try {
                result.setUploadDate(sdf.parse(date).getTime());
            } catch (ParseException e) {
                Log.e(TAG, "Parsing data error : " + date, e);
            }

            //Description
            result.setHtmlDescription(rows.get(rowIndex++).select(".right").html());
            //Tags
            result.getAttributes().put(AttributeType.TAG, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.TAG));
            result.setStatus(StatusContent.SAVED);

            //IsDownloadable
            result.setDownloadable(doc.select(".button.green:contains(Read Online)").size() > 0);

            result.setSite(Site.FAKKU);
        }
        return result;
    }

    private static Content parseContent(Element content) {
        Content result = new Content();
        Element contentTitle = content.select(".content-title").first();
        result.setUrl(contentTitle.attr("href"));
        result.setTitle(contentTitle.attr("title"));

        int rowIndex = 1;

        Elements rows = content.select(".row");
        //images
        result.setCoverImageUrl(content.select(".cover").attr("src"));
        //series
        result.getAttributes().put(AttributeType.SERIE, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.SERIE));
        //Artist
        result.getAttributes().put(AttributeType.ARTIST, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.ARTIST));
        //Publisher or Translator
        if (rows.get(rowIndex).select("div.left").html().equals("Publisher")) {
            result.getAttributes().put(AttributeType.PUBLISHER, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.PUBLISHER));
        } else if (rows.get(rowIndex).select("div.left").html().equals("Translator")) {
            result.getAttributes().put(AttributeType.TRANSLATOR, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.TRANSLATOR));
        }
        //Language
        result.getAttributes().put(AttributeType.LANGUAGE, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.LANGUAGE));
        //Description
        result.setHtmlDescription(rows.get(rowIndex++).select(".right").html());
        //Tags
        result.getAttributes().put(AttributeType.TAG, parseAttributes(rows.get(rowIndex++).select("a"), AttributeType.TAG));
        result.setStatus(StatusContent.SAVED);
        result.setSite(Site.FAKKU);
        return result;
    }

    private static Attribute parseAttribute(Element attribute, AttributeType type) {
        Attribute result = new Attribute();
        result.setName(attribute.text());
        result.setUrl(attribute.attr("href"));
        result.setType(type);
        return result;
    }

    private static List<Attribute> parseAttributes(Elements attributes, AttributeType type) {
        List<Attribute> result = null;

        if (attributes.size() > 0) {
            result = new ArrayList<>(attributes.size());
            for (Element attribute : attributes) {
                result.add(parseAttribute(attribute, type));
            }
        }

        return result;
    }
}
