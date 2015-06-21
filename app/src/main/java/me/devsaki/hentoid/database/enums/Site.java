package me.devsaki.hentoid.database.enums;

/**
 * Created by neko on 20/06/2015.
 */
public enum Site {

    FAKKU(0, "Fakku", "https://www.fakku.net"), PURURIN(1, "Pururin", "http://pururin.com");

    private int code;
    private String description;
    private String url;

    Site(int code, String description, String url) {
        this.code = code;
        this.description = description;
        this.url = url;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public static Site searchByCode(int code){

        for(Site s : Site.values()){
            if(s.getCode()==code)
                return s;
        }

        return null;
    }
}
