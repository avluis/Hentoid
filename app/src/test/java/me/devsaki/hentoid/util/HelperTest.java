package me.devsaki.hentoid.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HelperTest {

    @Test
    public void removeInvisibleChars() {
        assertEquals("a", StringHelper.removeNonPrintableChars("\uF8FFa"));
        assertEquals("a", StringHelper.removeNonPrintableChars("\uDC00a"));
        assertEquals("a", StringHelper.removeNonPrintableChars("\u000Ea"));
        assertEquals("a", StringHelper.removeNonPrintableChars("\u200Ea"));
        assertEquals("a", StringHelper.removeNonPrintableChars("\na"));
        assertEquals(" a", StringHelper.removeNonPrintableChars(" a"));
    }

    @Test
    public void isPresentAsWord() {
        assertTrue(StringHelper.isPresentAsWord("gog", "high gog"));
        assertTrue(StringHelper.isPresentAsWord("gog", "high:gog"));
        assertTrue(StringHelper.isPresentAsWord("gog", "high-gog"));
        assertTrue(StringHelper.isPresentAsWord("gog", "♀gog"));
        assertTrue(StringHelper.isPresentAsWord("gog", "gog♀"));
        assertTrue(StringHelper.isPresentAsWord("gog", "gog"));
        assertFalse(StringHelper.isPresentAsWord("gog", "goggers"));
        assertFalse(StringHelper.isPresentAsWord("gog", "gogog"));
    }
}