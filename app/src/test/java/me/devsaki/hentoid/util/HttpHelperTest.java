package me.devsaki.hentoid.util;

import org.junit.Test;

import me.devsaki.hentoid.util.network.HttpHelper;

import static org.junit.Assert.assertEquals;

public class HttpHelperTest {

    @Test
    public void getExtensionFromUri() {
        assertEquals("ext", HttpHelper.INSTANCE.getExtensionFromUri("http://u.ext"));
        assertEquals("ext", HttpHelper.INSTANCE.getExtensionFromUri("http://aa/u.ext"));
        assertEquals("ext", HttpHelper.INSTANCE.getExtensionFromUri("http://aa/u.1.ext"));
        assertEquals("ext", HttpHelper.INSTANCE.getExtensionFromUri("http://aa.bb/u.ext"));
        assertEquals("ext", HttpHelper.INSTANCE.getExtensionFromUri("http://aa.bb/a/u.ext?k"));
        assertEquals("ext", HttpHelper.INSTANCE.getExtensionFromUri("http://aa.bb/a/u.1.ext?k"));
        assertEquals("ext", HttpHelper.INSTANCE.getExtensionFromUri("http://aa.bb/a/u.ext?k.ext2"));
        assertEquals("ext", HttpHelper.INSTANCE.getExtensionFromUri("http://aa.bb/u.ext#ba"));
        assertEquals("ext", HttpHelper.INSTANCE.getExtensionFromUri("http://aa.bb/a/u.ext?k#ba"));
        assertEquals("", HttpHelper.INSTANCE.getExtensionFromUri("http://aa.bb/a/u?k#ba"));
        assertEquals("", HttpHelper.INSTANCE.getExtensionFromUri("http://aa.bb/a/u?k.ext2"));
        assertEquals("", HttpHelper.INSTANCE.getExtensionFromUri("http://aa.bb/u#ba"));
    }

    @Test
    public void fixUrl() {
        assertEquals("http://abc.com/images", HttpHelper.INSTANCE.fixUrl("images", "http://abc.com"));
        assertEquals("http://abc.com/images", HttpHelper.INSTANCE.fixUrl("images", "http://abc.com/"));
        assertEquals("http://abc.com/images", HttpHelper.INSTANCE.fixUrl("/images", "http://abc.com"));
        assertEquals("http://abc.com/images", HttpHelper.INSTANCE.fixUrl("/images", "http://abc.com/"));
        assertEquals("http://abc.com/images", HttpHelper.INSTANCE.fixUrl("http://abc.com/images", "http://abc.com/"));
        assertEquals("http://abc.com/images", HttpHelper.INSTANCE.fixUrl("http://abc.com/images", "http://abc.com"));
        assertEquals("https://abc.com/images", HttpHelper.INSTANCE.fixUrl("//abc.com/images", "http://abc.com/"));
        assertEquals("https://abc.com/images", HttpHelper.INSTANCE.fixUrl("//abc.com/images", "http://abc.com"));
    }

    @Test
    public void cleanWebViewAgent() {
        assertEquals("Mozilla/5.0 (Linux; Android 10; AAA-BBB) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4444.75 Mobile Safari/537.36", HttpHelper.INSTANCE.cleanWebViewAgent("Mozilla/5.0 (Linux; Android 10; AAA-BBB; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4444.75 Mobile Safari/537.36"));
    }
}