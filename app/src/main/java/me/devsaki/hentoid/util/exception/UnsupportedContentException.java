package me.devsaki.hentoid.util.exception;

public class UnsupportedContentException extends Exception {

    private String result;

    public UnsupportedContentException(String result) {
        this.result = result;
    }

    @Override
    public String getMessage() {
        return result;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
