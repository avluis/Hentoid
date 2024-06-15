package me.devsaki.hentoid.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static me.devsaki.hentoid.util.StringHelperKt.isPresentAsWord;
import static me.devsaki.hentoid.util.StringHelperKt.removeNonPrintableChars;

import org.junit.Test;

public class HelperTest {

    @Test
    public void removeInvisibleChars() {
        assertEquals("a", removeNonPrintableChars("\uF8FFa"));
        assertEquals("a", removeNonPrintableChars("\uDC00a"));
        assertEquals("a", removeNonPrintableChars("\u000Ea"));
        assertEquals("a", removeNonPrintableChars("\u200Ea"));
        assertEquals("a", removeNonPrintableChars("\na"));
        assertEquals(" a", removeNonPrintableChars(" a"));
    }

    @Test
    public void isPresentAsWordTest() {
        assertTrue(isPresentAsWord("gog", "high gog"));
        assertTrue(isPresentAsWord("gog", "high:gog"));
        assertTrue(isPresentAsWord("gog", "high-gog"));
        assertTrue(isPresentAsWord("gog", "♀gog"));
        assertTrue(isPresentAsWord("gog", "gog♀"));
        assertTrue(isPresentAsWord("gog", "gog"));
        assertFalse(isPresentAsWord("gog", "goggers"));
        assertFalse(isPresentAsWord("gog", "gogog"));
    }
}