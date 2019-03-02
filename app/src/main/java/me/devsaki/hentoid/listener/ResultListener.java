package me.devsaki.hentoid.listener;

public interface ResultListener<T> {
    void onResultReady(T results, long totalResults);

    void onResultFailed(String message);
}

