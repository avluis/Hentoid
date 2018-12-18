package me.devsaki.hentoid.parsers;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.util.AttributeMap;

public class ParseHelper {
    public static void parseAttributes(AttributeMap map, AttributeType type, List<Element> elements) {
        parseAttributes(map, type, elements, false);
    }

    public static void parseAttributes(AttributeMap map, AttributeType type, List<Element> elements, boolean filterCount) {
        if (elements != null)
            for (Element a : elements) {
                String name = a.text();
                if (filterCount) {
                    // Remove counters from metadata (e.g. "Futanari (2660)" => "Futanari")
                    int bracketPos = name.lastIndexOf("(");
                    if (bracketPos > 1 && ' ' == name.charAt(bracketPos - 1)) bracketPos--;
                    if (bracketPos > -1) name = name.substring(0, bracketPos);
                }
                Attribute attribute = new Attribute(type, name, a.attr("href"));

                map.add(attribute);
            }
    }
}
