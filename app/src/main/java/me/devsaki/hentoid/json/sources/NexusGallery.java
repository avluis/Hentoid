package me.devsaki.hentoid.json.sources;

import com.annimon.stream.Stream;

import java.util.List;

public class NexusGallery {
    private String b;
    private String r;
    private String i;
    private List<NexusPage> f;

    public List<String> toUrls() {
        return Stream.of(f).map(page -> b + r + page.h + "/" + i + "/" + page.p).toList();
    }

    static class NexusPage {
        private String h;
        private String p;
    }
}
