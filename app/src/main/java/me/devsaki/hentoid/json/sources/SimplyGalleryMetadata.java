package me.devsaki.hentoid.json.sources;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class SimplyGalleryMetadata {
    Props props;

    private static class Props {
        PageProps pageProps;
    }

    private static class PageProps {
        PagesData data;
    }

    private static class PagesData {
        List<SimplyContentMetadata.PageData> pages;
    }

    public List<String> getPageUrls() {
        List<String> result = new ArrayList<>();
        if (null == props
                || null == props.pageProps
                || null == props.pageProps.data
                || null == props.pageProps.data.pages) return result;

        List<SimplyContentMetadata.PageData> pages = Stream.of(props.pageProps.data.pages).sortBy(p -> p.pageNum).toList();

        for (SimplyContentMetadata.PageData page : pages) {
            String url = page.getFullUrl();
            if (!url.isEmpty()) result.add(url);
        }

        return result;
    }
}
