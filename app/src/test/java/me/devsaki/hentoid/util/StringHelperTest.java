package me.devsaki.hentoid.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StringHelperTest {

    @Test
    public void cleanup() {
        assertEquals("word", StringHelper.cleanup("[hi] word: [tag]"));
        assertEquals("", StringHelper.cleanup(""));
        assertEquals("a®a", StringHelper.cleanup("a&#174;a"));
        assertEquals("word", StringHelper.cleanup("[word!]"));
    }
}