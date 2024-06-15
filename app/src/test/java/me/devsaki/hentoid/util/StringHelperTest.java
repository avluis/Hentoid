package me.devsaki.hentoid.util;

import static org.junit.Assert.assertEquals;
import static me.devsaki.hentoid.util.StringHelperKt.simplify;

import org.junit.Test;

public class StringHelperTest {

    @Test
    public void cleanup() {
        assertEquals("word", simplify("[hi] word: [tag]"));
        assertEquals("", simplify(""));
        assertEquals("a®a", simplify("a&#174;a"));
        assertEquals("word", simplify("[word!]"));
    }
}