package me.devsaki.hentoid.database

import androidx.paging.DataSource
import androidx.paging.PositionalDataSource
import io.objectbox.reactive.DataObserver
import java.util.Arrays

// Inspired from ObjectBoxDataSource
class ObjectBoxPredeterminedDataSource<T>(
    private val fetcher: (List<Long>) -> List<T>,
    private val ids: LongArray
) : PositionalDataSource<T>() {
    private val observer: DataObserver<List<T>> = DataObserver { this.invalidate() }

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<T>) {
        val totalCount = ids.size
        if (totalCount == 0) {
            callback.onResult(emptyList(), 0, 0)
        } else {
            val position = computeInitialLoadPosition(params, totalCount)
            val loadSize = computeInitialLoadSize(params, position, totalCount)
            val list = this.loadRange(position, loadSize)
            if (list.size == loadSize) {
                callback.onResult(list, position, totalCount)
            } else {
                invalidate()
            }
        }
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<T>) {
        callback.onResult(this.loadRange(params.startPosition, params.loadSize))
    }

    private fun loadRange(startPosition: Int, loadCount: Int): List<T> {
        val subRange = Arrays.copyOfRange(ids, startPosition, startPosition + loadCount)
        return fetcher.invoke(subRange.asList())
    }


    class PredeterminedDataSourceFactory<I> internal constructor(
        private val fetcher: (List<Long>) -> List<I>,
        private val ids: LongArray
    ) : Factory<Int, I>() {
        override fun create(): DataSource<Int, I> {
            return ObjectBoxPredeterminedDataSource(fetcher, ids)
        }
    }
}