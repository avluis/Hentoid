package me.devsaki.hentoid.json.sources;

@SuppressWarnings("unused, MismatchedQueryAndUpdateOfCollection")
public class EHentaiImageQuery {
    private final String method = "imagedispatch";
    private final Integer gid;
    private final String imgkey;
    private final String mpvkey;
    private final Integer page;

    public EHentaiImageQuery(
            int gid,
            String imgKey,
            String mpvKey,
            int page) {
        this.gid = gid;
        this.imgkey = imgKey;
        this.mpvkey = mpvKey;
        this.page = page;
    }
}
