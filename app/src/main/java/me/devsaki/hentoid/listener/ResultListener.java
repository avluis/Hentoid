package me.devsaki.hentoid.listener;

public interface ResultListener<T> {
    void onResultReady(T results, int totalContent);

    void onResultFailed(String message);
}

