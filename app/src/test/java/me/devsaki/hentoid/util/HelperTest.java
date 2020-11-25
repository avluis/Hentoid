package me.devsaki.hentoid.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HelperTest {

    @Test
    public void removeInvisibleChars() {
        assertEquals("a", Helper.removeNonPrintableChars("\uF8FFa"));
        assertEquals("a", Helper.removeNonPrintableChars("\uDC00a"));
        assertEquals("a", Helper.removeNonPrintableChars("\u000Ea"));
        assertEquals("a", Helper.removeNonPrintableChars("\u200Ea"));
        assertEquals("a", Helper.removeNonPrintableChars("\na"));
        assertEquals(" a", Helper.removeNonPrintableChars(" a"));
    }

    @Test
    public void isPresentAsWord() {
        assertTrue(Helper.isPresentAsWord("gog", "high gog"));
        assertTrue(Helper.isPresentAsWord("gog", "high:gog"));
        assertTrue(Helper.isPresentAsWord("gog", "high-gog"));
        assertTrue(Helper.isPresentAsWord("gog", "♀gog"));
        assertTrue(Helper.isPresentAsWord("gog", "gog♀"));
        assertTrue(Helper.isPresentAsWord("gog", "gog"));
        assertFalse(Helper.isPresentAsWord("gog", "goggers"));
        assertFalse(Helper.isPresentAsWord("gog", "gogog"));
    }
}