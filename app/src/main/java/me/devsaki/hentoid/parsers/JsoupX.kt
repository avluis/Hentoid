package me.devsaki.hentoid.parsers

import org.jsoup.nodes.Element
import org.jsoup.select.Elements

// Unique select method supporting both XPath (if query starts by $x) and XSS (if not)
fun Element.selectX(query: String): Elements {
    return if (query.startsWith("\$x")) this.selectXpath(query.substring(2))
    else this.select(query)
}