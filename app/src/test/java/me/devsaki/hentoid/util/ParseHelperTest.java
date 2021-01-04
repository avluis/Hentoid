package me.devsaki.hentoid.util;

import org.junit.Test;

import me.devsaki.hentoid.parsers.ParseHelper;

import static org.junit.Assert.assertEquals;

public class ParseHelperTest {

    @Test
    public void removeSquareBrackets() {
        assertEquals("aaa", ParseHelper.removeTextualTags("aaa"));
        assertEquals("", ParseHelper.removeTextualTags(""));
        assertEquals("aaa", ParseHelper.removeTextualTags("[stuff] aaa"));
        assertEquals("aaa", ParseHelper.removeTextualTags("[stuff] aaa [things]"));
        assertEquals("aaa", ParseHelper.removeTextualTags("[stuff] aaa[things]"));
        assertEquals("a aa", ParseHelper.removeTextualTags("[stuff] a [things] aa"));
        assertEquals("a a a", ParseHelper.removeTextualTags("a[stuff] a [things] a [bits] "));
    }
}