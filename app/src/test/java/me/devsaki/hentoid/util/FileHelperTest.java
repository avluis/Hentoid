package me.devsaki.hentoid.util;

import static org.junit.Assert.assertEquals;
import static me.devsaki.hentoid.util.file.FileHelperKt.cleanFileName;

import org.junit.Test;


public class FileHelperTest {

    @Test
    public void cleanFileNameTest() {
        assertEquals("aa", cleanFileName("a?a"));
        assertEquals("aa", cleanFileName("a\"a"));
        assertEquals("aa", cleanFileName("a*a"));
        assertEquals("aa", cleanFileName("a/a"));
        assertEquals("aa", cleanFileName("a:a"));
        assertEquals("aa", cleanFileName("a<a"));
        assertEquals("aa", cleanFileName("a>a"));
        assertEquals("aa", cleanFileName("a?a"));
        assertEquals("aa", cleanFileName("a\\a"));
        assertEquals("aa", cleanFileName("a|a"));
    }
}