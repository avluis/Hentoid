package me.devsaki.hentoid.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}