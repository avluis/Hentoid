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

    public void getDomainFromUri() {
        /* Unfortunately, android.net.Uri is a stub when running unit tests :(
        assertEquals("domain.ext", HttpHelper.getDomainFromUri("http://domain.ext/aaa?ooo"));
        assertEquals("domain.com", HttpHelper.getDomainFromUri("http://www.domain.com/u.ext"));
        assertEquals("domain.com", HttpHelper.getDomainFromUri("www.domain.com/aa/u.1.ext"));
         */
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

    @Test
    public void cleanWebViewAgent() {
        assertEquals("Mozilla/5.0 (Linux; Android 10; AAA-BBB) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4444.75 Mobile Safari/537.36", HttpHelper.cleanWebViewAgent("Mozilla/5.0 (Linux; Android 10; AAA-BBB; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4444.75 Mobile Safari/537.36"));
    }
}