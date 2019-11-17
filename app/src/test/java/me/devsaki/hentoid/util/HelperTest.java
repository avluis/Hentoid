package me.devsaki.hentoid.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HelperTest {

    @Test
    public void removeInvisibleChars() {
        assertEquals("a", Helper.removeInvisibleChars("\uF8FFa"));
        assertEquals("a", Helper.removeInvisibleChars("\uDC00a"));
        assertEquals("a", Helper.removeInvisibleChars("\u000Ea"));
        assertEquals("a", Helper.removeInvisibleChars("\u200Ea"));
        assertEquals("a", Helper.removeInvisibleChars("\na"));
        assertEquals(" a", Helper.removeInvisibleChars(" a"));
    }
}