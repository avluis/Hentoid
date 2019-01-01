package me.devsaki.hentoid.parsers;

import org.jsoup.nodes.Element;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.util.AttributeMap;

public class ParseHelper {
    /**
     * Remove counters from given string (e.g. "Futanari (2660)" => "Futanari")
     *
     * @param s String brackets have to be removed
     * @return String with removed brackets
     */
    public static String removeBrackets(String s) {
        int bracketPos = s.lastIndexOf("(");
        if (bracketPos > 1 && ' ' == s.charAt(bracketPos - 1)) bracketPos--;
        if (bracketPos > -1) {
            return s.substring(0, bracketPos);
        }

        return s;
    }

    public static void parseAttributes(AttributeMap map, AttributeType type, List<Element> elements, boolean filterCount) {
        if (elements != null)
            for (Element a : elements) {
                String name = a.text();
                if (filterCount) name = removeBrackets(name);
                Attribute attribute = new Attribute(type, name, a.attr("href"));

                map.add(attribute);
            }
    }
}
