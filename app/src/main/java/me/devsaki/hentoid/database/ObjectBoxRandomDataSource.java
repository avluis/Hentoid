package me.devsaki.hentoid.database;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import io.objectbox.query.LazyList;
import io.objectbox.query.Query;
import io.objectbox.reactive.DataObserver;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.util.RandomSeedSingleton;

// Inspired from ObjectBoxDataSource
class ObjectBoxRandomDataSource<T> extends PositionalDataSource<T> {
    private final Query<T> query;
    private final DataObserver<List<T>> observer;

    private ObjectBoxRandomDataSource(Query<T> query) {
        this.query = query;
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
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < lazyList.size(); i++) order.add(i);
        Collections.shuffle(order, new Random(RandomSeedSingleton.getInstance().getSeed(Consts.SEED_CONTENT)));

        int maxPage = Math.min(startPosition + loadCount, order.size());

        List<T> result = new ArrayList<>();
        for (int i = startPosition; i < maxPage; i++) {
            result.add(lazyList.get(order.get(i)));
        }

        return result;
    }

    public static class RandomDataSourceFactory<I> extends androidx.paging.DataSource.Factory<Integer, I> {
        private final Query<I> query;

        RandomDataSourceFactory(Query<I> query) {
            this.query = query;
        }

        @NonNull
        public DataSource<Integer, I> create() {
            return new ObjectBoxRandomDataSource<>(query);
        }
    }

}
