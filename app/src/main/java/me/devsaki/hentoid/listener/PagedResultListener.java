package me.devsaki.hentoid.listener;

import java.util.List;

public interface PagedResultListener<T> {
    void onPagedResultReady(List<T> results, long totalSelected, long total);

    void onPagedResultFailed(T result, String message);
}

