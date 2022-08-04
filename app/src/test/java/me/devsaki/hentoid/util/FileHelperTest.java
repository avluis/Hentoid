package me.devsaki.hentoid.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import me.devsaki.hentoid.util.file.FileHelper;

public class FileHelperTest {

    @Test
    public void cleanFileName() {
        assertEquals("aa", FileHelper.cleanFileName("a?a"));
        assertEquals("aa", FileHelper.cleanFileName("a\"a"));
        assertEquals("aa", FileHelper.cleanFileName("a*a"));
        assertEquals("aa", FileHelper.cleanFileName("a/a"));
        assertEquals("aa", FileHelper.cleanFileName("a:a"));
        assertEquals("aa", FileHelper.cleanFileName("a<a"));
        assertEquals("aa", FileHelper.cleanFileName("a>a"));
        assertEquals("aa", FileHelper.cleanFileName("a?a"));
        assertEquals("aa", FileHelper.cleanFileName("a\\a"));
        assertEquals("aa", FileHelper.cleanFileName("a|a"));
    }
}