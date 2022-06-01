package me.devsaki.hentoid.database;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.objectbox.query.LazyList;
import io.objectbox.query.Query;
import io.objectbox.reactive.DataObserver;
import me.devsaki.hentoid.util.Helper;

// Inspired from ObjectBoxDataSource
class ObjectBoxRandomDataSource<T> extends PositionalDataSource<T> {
    private final Query<T> query;
    private final DataObserver<List<T>> observer;
    private final List<Long> shuffledList;
    private final Map<Long, Integer> idsToQueryListIndexes = new HashMap<>();

    private ObjectBoxRandomDataSource(Query<T> query, List<Long> shuffleIds) {
        this.query = query;

        long[] queryIds = query.findIds();
        Set<Long> queryIdSet = Helper.getSetFromPrimitiveArray(queryIds);
        int idx = 0;
        for (Long id : queryIds) idsToQueryListIndexes.put(id, idx++);

        LinkedHashSet<Long> shuffledSet = new LinkedHashSet<>(shuffleIds.size());
        shuffledSet.addAll(shuffleIds);

        // Keep common IDs (intersect)
        shuffledSet.retainAll(queryIdSet);

        // Isolate new IDs that have never been shuffled and append them at the end
        if (shuffledSet.size() < queryIdSet.size()) {
            queryIdSet.removeAll(shuffledSet);
            shuffledSet.addAll(queryIdSet);
        }
        shuffledList = Stream.of(shuffledSet).toList();

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
        LazyList<T> lazyList = query.findLazy();

        int maxPage = Math.min(startPosition + loadCount, shuffledList.size());

        List<T> result = new ArrayList<>();
        for (int i = startPosition; i < maxPage; i++) {
            Integer index = idsToQueryListIndexes.get(shuffledList.get(i));
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
