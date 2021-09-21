package me.devsaki.hentoid.json.sources;

@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class EHentaiImageResponse {
    private String lf;
    private String ls;
    private String ll;
    private String lo;
    private String i; // image displayed in the multipage viewer
    private String s;

    public String getUrl() {
        return i;
    }
}
