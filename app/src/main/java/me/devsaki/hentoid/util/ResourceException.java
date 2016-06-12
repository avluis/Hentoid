package me.devsaki.hentoid.util;

/**
 * Created by avluis on 06/12/2016.
 * Resource ID Exception
 */
public class ResourceException extends Exception {
    private String result;
    private Exception code;

    public ResourceException(String result, Exception code) {
        this.result = result;
        this.code = code;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Exception getCode() {
        return code;
    }

    public void setCode(Exception code) {
        this.code = code;
    }
}
