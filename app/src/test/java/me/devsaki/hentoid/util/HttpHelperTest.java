package me.devsaki.hentoid.util;

import org.junit.Test;

import me.devsaki.hentoid.util.network.HttpHelper;

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
    public void fixUrl() {
        assertEquals("http://abc.com/images", HttpHelper.fixUrl("images", "http://abc.com"));
        assertEquals("http://abc.com/images", HttpHelper.fixUrl("images", "http://abc.com/"));
        assertEquals("http://abc.com/images", HttpHelper.fixUrl("/images", "http://abc.com"));
        assertEquals("http://abc.com/images", HttpHelper.fixUrl("/images", "http://abc.com/"));
        assertEquals("http://abc.com/images", HttpHelper.fixUrl("http://abc.com/images", "http://abc.com/"));
        assertEquals("http://abc.com/images", HttpHelper.fixUrl("http://abc.com/images", "http://abc.com"));
        assertEquals("https://abc.com/images", HttpHelper.fixUrl("//abc.com/images", "http://abc.com/"));
        assertEquals("https://abc.com/images", HttpHelper.fixUrl("//abc.com/images", "http://abc.com"));
    }
}