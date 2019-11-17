package me.devsaki.hentoid.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HttpHelperTest {

    @Test
    public void getExtensionFromUri() {
        assertEquals("ext", HttpHelper.getExtensionFromUri("http://u.ext"));
        assertEquals("ext", HttpHelper.getExtensionFromUri("http://aa/u.ext"));
        assertEquals("ext", HttpHelper.getExtensionFromUri("http://aa/u.1.ext"));
        assertEquals("ext", HttpHelper.getExtensionFromUri("http://aa.bb/u.ext"));
        assertEquals("ext", HttpHelper.getExtensionFromUri("http://aa.bb/a/u.ext?k"));
        assertEquals("ext", HttpHelper.getExtensionFromUri("http://aa.bb/a/u.1.ext?k"));
        assertEquals("ext", HttpHelper.getExtensionFromUri("http://aa.bb/a/u.ext?k.ext2"));
    }

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