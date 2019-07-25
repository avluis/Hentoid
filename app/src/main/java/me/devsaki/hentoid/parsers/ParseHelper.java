package me.devsaki.hentoid.parsers;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadPreparationEvent;
import me.devsaki.hentoid.util.AttributeMap;

public class ParseHelper {
    /**
     * Remove counters from given string (e.g. "Futanari (2660)" => "Futanari")
     *
     * @param s String brackets have to be removed
     * @return String with removed brackets
     */
    public static String removeBrackets(String s) {
        int bracketPos = s.lastIndexOf('(');
        if (bracketPos > 1 && ' ' == s.charAt(bracketPos - 1)) bracketPos--;
        if (bracketPos > -1) {
            return s.substring(0, bracketPos);
        }

        return s;
    }

    public static void parseAttributes(AttributeMap map, AttributeType type, List<Element> elements, boolean filterCount, Site site) {
        if (elements != null)
            for (Element a : elements) parseAttribute(map, type, a, filterCount, site);
    }

    public static void parseAttribute(AttributeMap map, AttributeType type, Element element, boolean filterCount, Site site) {
        String name = element.text();
        if (filterCount) name = removeBrackets(name);
        Attribute attribute = new Attribute(type, name, element.attr("href"), site);

        map.add(attribute);
    }

    static ImageFile urlToImageFile(@Nonnull String imgUrl, int order) {
        ImageFile result = new ImageFile();

        String name = String.format(Locale.US, "%03d", order);
        result.setName(name).setOrder(order).setUrl(imgUrl).setStatus(StatusContent.ONLINE);

        return result;
    }

    static List<ImageFile> urlsToImageFiles(@Nonnull List<String> imgUrls) {
        List<ImageFile> result = new ArrayList<>();

        int order = 1;
        for (String s : imgUrls) result.add(urlToImageFile(s, order++));

        return result;
    }

    static void signalProgress(int current, int max) {
        EventBus.getDefault().post(new DownloadPreparationEvent(current, max));
    }
}
