package me.devsaki.hentoid.database.enums;

import me.devsaki.hentoid.R;

/**
 * Created by neko on 20/06/2015.
 */
public enum Site {

    FAKKU(0, "Fakku", "https://www.fakku.net", R.drawable.ic_fakku), PURURIN(1, "Pururin", "http://pururin.com", R.drawable.ic_pururin);

    private int code;
    private String description;
    private String url;
    private int ico;

    Site(int code, String description, String url, int ico) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.ico = ico;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public int getIco() {
        return ico;
    }

    public static Site searchByCode(int code){

        for(Site s : Site.values()){
            if(s.getCode()==code)
                return s;
        }

        return null;
    }
}
