package me.devsaki.hentoid.util;

import static org.junit.Assert.assertEquals;
import static me.devsaki.hentoid.parsers.ParseHelperKt.removeTextualTags;

import org.junit.Test;

public class ParseHelperTest {

    @Test
    public void removeSquareBrackets() {
        assertEquals("aaa", removeTextualTags("aaa"));
        assertEquals("", removeTextualTags(""));
        assertEquals("aaa", removeTextualTags("[stuff] aaa"));
        assertEquals("aaa", removeTextualTags("[stuff] aaa [things]"));
        assertEquals("aaa", removeTextualTags("[stuff] aaa[things]"));
        assertEquals("a aa", removeTextualTags("[stuff] a [things] aa"));
        assertEquals("a a a", removeTextualTags("a[stuff] a [things] a [bits] "));
    }
}