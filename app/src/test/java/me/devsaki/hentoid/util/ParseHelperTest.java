package me.devsaki.hentoid.util;

import org.junit.Test;

import me.devsaki.hentoid.parsers.ParseHelper;

import static org.junit.Assert.assertEquals;

public class ParseHelperTest {

    @Test
    public void removeSquareBrackets() {
        assertEquals("aaa", ParseHelper.INSTANCE.removeTextualTags("aaa"));
        assertEquals("", ParseHelper.INSTANCE.removeTextualTags(""));
        assertEquals("aaa", ParseHelper.INSTANCE.removeTextualTags("[stuff] aaa"));
        assertEquals("aaa", ParseHelper.INSTANCE.removeTextualTags("[stuff] aaa [things]"));
        assertEquals("aaa", ParseHelper.INSTANCE.removeTextualTags("[stuff] aaa[things]"));
        assertEquals("a aa", ParseHelper.INSTANCE.removeTextualTags("[stuff] a [things] aa"));
        assertEquals("a a a", ParseHelper.INSTANCE.removeTextualTags("a[stuff] a [things] a [bits] "));
    }
}