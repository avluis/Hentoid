package me.devsaki.hentoid.util.exception;

public class LimitReachedException extends Exception {
    private String result;

    public LimitReachedException(String result) {
        this.result = result;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
