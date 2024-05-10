package me.devsaki.hentoid.util;

import static org.junit.Assert.assertEquals;
import static me.devsaki.hentoid.util.network.HttpHelperKt.cleanWebViewAgent;
import static me.devsaki.hentoid.util.network.HttpHelperKt.fixUrl;
import static me.devsaki.hentoid.util.network.HttpHelperKt.getExtensionFromUri;

import org.junit.Test;

public class HttpHelperTest {

    @Test
    public void getExtensionFromUriTest() {
        assertEquals("ext", getExtensionFromUri("http://u.ext"));
        assertEquals("ext", getExtensionFromUri("http://aa/u.ext"));
        assertEquals("ext", getExtensionFromUri("http://aa/u.1.ext"));
        assertEquals("ext", getExtensionFromUri("http://aa.bb/u.ext"));
        assertEquals("ext", getExtensionFromUri("http://aa.bb/a/u.ext?k"));
        assertEquals("ext", getExtensionFromUri("http://aa.bb/a/u.1.ext?k"));
        assertEquals("ext", getExtensionFromUri("http://aa.bb/a/u.ext?k.ext2"));
        assertEquals("ext", getExtensionFromUri("http://aa.bb/u.ext#ba"));
        assertEquals("ext", getExtensionFromUri("http://aa.bb/a/u.ext?k#ba"));
        assertEquals("", getExtensionFromUri("http://aa.bb/a/u?k#ba"));
        assertEquals("", getExtensionFromUri("http://aa.bb/a/u?k.ext2"));
        assertEquals("", getExtensionFromUri("http://aa.bb/u#ba"));
    }

    @Test
    public void fixUrlTest() {
        assertEquals("http://abc.com/images", fixUrl("images", "http://abc.com"));
        assertEquals("http://abc.com/images", fixUrl("images", "http://abc.com/"));
        assertEquals("http://abc.com/images", fixUrl("/images", "http://abc.com"));
        assertEquals("http://abc.com/images", fixUrl("/images", "http://abc.com/"));
        assertEquals("http://abc.com/images", fixUrl("http://abc.com/images", "http://abc.com/"));
        assertEquals("http://abc.com/images", fixUrl("http://abc.com/images", "http://abc.com"));
        assertEquals("https://abc.com/images", fixUrl("//abc.com/images", "http://abc.com/"));
        assertEquals("https://abc.com/images", fixUrl("//abc.com/images", "http://abc.com"));
    }

    @Test
    public void cleanWebViewAgentTest() {
        assertEquals("Mozilla/5.0 (Linux; Android 10; AAA-BBB) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4444.75 Mobile Safari/537.36", cleanWebViewAgent("Mozilla/5.0 (Linux; Android 10; AAA-BBB; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4444.75 Mobile Safari/537.36"));
    }
}