package me.devsaki.hentoid.database;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import com.annimon.stream.function.Function;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.objectbox.reactive.DataObserver;
import me.devsaki.hentoid.util.Helper;

// Inspired from ObjectBoxDataSource
class ObjectBoxPredeterminedDataSource<T> extends PositionalDataSource<T> {
    private final Function<List<Long>, List<T>> fetcher;
    private final long[] ids;
    private final DataObserver<List<T>> observer;

    private ObjectBoxPredeterminedDataSource(Function<List<Long>, List<T>> fetcher, long[] ids) {
        this.fetcher = fetcher;
        this.ids = ids;
        this.observer = data -> ObjectBoxPredeterminedDataSource.this.invalidate();
    }

    public void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback<T> callback) {
        int totalCount = ids.length;
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

    public void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback<T> callback) {
        callback.onResult(this.loadRange(params.startPosition, params.loadSize));
    }

    private List<T> loadRange(int startPosition, int loadCount) {
        long[] subRange = Arrays.copyOfRange(ids, startPosition, startPosition + loadCount);
        return fetcher.apply(Helper.getListFromPrimitiveArray(subRange));
    }

    public static class PredeterminedDataSourceFactory<I> extends Factory<Integer, I> {
        private final Function<List<Long>, List<I>> fetcher;
        private final long[] ids;

        PredeterminedDataSourceFactory(Function<List<Long>, List<I>> fetcher, long[] ids) {
            this.fetcher = fetcher;
            this.ids = ids;
        }

        @NonNull
        public DataSource<Integer, I> create() {
            return new ObjectBoxPredeterminedDataSource<>(fetcher, ids);
        }
    }

}
