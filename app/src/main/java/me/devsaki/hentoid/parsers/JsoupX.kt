package me.devsaki.hentoid.parsers

import org.jsoup.nodes.Element
import org.jsoup.select.Elements

fun Element.selectX(query: String): Elements {
    return if (query.startsWith("\$x")) this.selectXpath(query.substring(2))
    else this.select(query)
}