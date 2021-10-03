package me.devsaki.hentoid.json.sources;

import com.annimon.stream.Stream;

import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.util.StringHelper;

@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class PixivGalleryMetadata {

    private Boolean error;
    private String message;
    private List<PixivImage> body;

    public List<String> getPageUrls() {
        return Stream.of(body).map(PixivImage::getPageUrl).toList();
    }

    public boolean isError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    private static class PixivImage {
        private Map<String, String> urls;

        String getPageUrl() {
            if (null == urls) return "";
            String result = urls.get("original");
            if (null == result) result = urls.get("regular");
            return StringHelper.protect(result);
        }
    }
}
