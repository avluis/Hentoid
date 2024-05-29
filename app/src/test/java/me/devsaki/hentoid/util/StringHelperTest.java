package me.devsaki.hentoid.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StringHelperTest {

    @Test
    public void cleanup() {
        assertEquals("word", StringHelper.simplify("[hi] word: [tag]"));
        assertEquals("", StringHelper.simplify(""));
        assertEquals("a®a", StringHelper.simplify("a&#174;a"));
        assertEquals("word", StringHelper.simplify("[word!]"));
    }
}