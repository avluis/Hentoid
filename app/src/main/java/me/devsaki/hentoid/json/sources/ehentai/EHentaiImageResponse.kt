package me.devsaki.hentoid.json.sources.ehentai;

@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class EHentaiImageResponse {
    private String lf; // Relative link to the full-size image
    private String ls;
    private String ll;
    private String lo;
    private String i; // image displayed in the multipage viewer
    private String s;

    public String getUrl() {
        return i;
    }

    public String getFullUrlRelative() {
        return lf;
    }
}
