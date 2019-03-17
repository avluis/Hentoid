package me.devsaki.hentoid.util;

/**
 * Created by avluis on 06/12/2016.
 * Attribute Exception
 */
public class JSONParseException extends Exception {
    private String result;

    public JSONParseException(String result) {
        this.result = result;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
