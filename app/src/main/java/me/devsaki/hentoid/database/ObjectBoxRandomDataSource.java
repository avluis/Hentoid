package me.devsaki.hentoid.database;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.objectbox.query.LazyList;
import io.objectbox.query.Query;
import io.objectbox.reactive.DataObserver;
import me.devsaki.hentoid.util.Helper;

// Inspired from ObjectBoxDataSource
class ObjectBoxRandomDataSource<T> extends PositionalDataSource<T> {
    private final Query<T> query;
    private final DataObserver<List<T>> observer;
    private final List<Long> shuffleIds;

    private ObjectBoxRandomDataSource(Query<T> query, List<Long> shuffleIds) {
        this.query = query;
        this.shuffleIds = shuffleIds;
        this.observer = data -> ObjectBoxRandomDataSource.this.invalidate();
        query.subscribe().onlyChanges().weak().observer(this.observer);
    }

    public void loadInitial(@NonNull PositionalDataSource.LoadInitialParams params, @NonNull PositionalDataSource.LoadInitialCallback<T> callback) {
        int totalCount = (int) this.query.count();
        if (totalCount == 0) {
            callback.onResult(Collections.emptyList(), 0, 0);
        } else {
            int position = computeInitialLoadPosition(params, totalCount);
            int loadSize = computeInitialLoadSize(params, position, totalCount);
            List<T> list = this.loadRange(position, loadSize);
            if (list.size() == loadSize) {
                callback.onResult(list, position, totalCount);
            } else {
                this.invalidate();
            }
        }
    }

    public void loadRange(@NonNull PositionalDataSource.LoadRangeParams params, @NonNull PositionalDataSource.LoadRangeCallback<T> callback) {
        callback.onResult(this.loadRange(params.startPosition, params.loadSize));
    }

    private List<T> loadRange(int startPosition, int loadCount) {
        return shuffleRandomSort(this.query, startPosition, loadCount);
    }

    private List<T> shuffleRandomSort(Query<T> query, int startPosition, int loadCount) {
        LazyList<T> lazyList = query.findLazy();
        List<Long> queryIds = Helper.getListFromPrimitiveArray(query.findIds());
        Map<Long, Integer> idsToIndexes = new HashMap<>();
        for (int i = 0; i < queryIds.size(); i++) {
            idsToIndexes.put(queryIds.get(i), i);
        }

        // Keep common IDs
        shuffleIds.retainAll(queryIds);

        // Isolate new IDs that have never been shuffled and append them at the end
        if (shuffleIds.size() < queryIds.size()) {
            queryIds.removeAll(shuffleIds);
            shuffleIds.addAll(queryIds);
        }

        int maxPage = Math.min(startPosition + loadCount, shuffleIds.size());

        List<T> result = new ArrayList<>();
        for (int i = startPosition; i < maxPage; i++) {
            Integer index = idsToIndexes.get(shuffleIds.get(i));
            if (index != null)
                result.add(lazyList.get(index));
        }

        return result;
    }

    public static class RandomDataSourceFactory<I> extends androidx.paging.DataSource.Factory<Integer, I> {
        private final Query<I> query;
        private final List<Long> shuffleIds;

        RandomDataSourceFactory(Query<I> query, List<Long> shuffleIds) {
            this.query = query;
            this.shuffleIds = shuffleIds;
        }

        @NonNull
        public DataSource<Integer, I> create() {
            return new ObjectBoxRandomDataSource<>(query, shuffleIds);
        }
    }

}
