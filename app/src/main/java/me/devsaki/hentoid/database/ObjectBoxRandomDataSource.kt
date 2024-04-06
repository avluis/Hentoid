package me.devsaki.hentoid.database

import androidx.paging.DataSource
import androidx.paging.PositionalDataSource
import io.objectbox.query.Query
import io.objectbox.reactive.DataObserver
import kotlin.math.min

// Inspired from ObjectBoxDataSource
class ObjectBoxRandomDataSource<T>(
    private val query: Query<T>,
    shuffleIds: List<Long>
) : PositionalDataSource<T>() {
    private val shuffledList: List<Long>
    private val observer: DataObserver<List<T>>
    private val idsToQueryListIndexes: MutableMap<Long, Int> = HashMap()

    init {
        val queryIds = query.findIds()
        val queryIdSet: MutableSet<Long> = queryIds.toHashSet()
        var idx = 0
        for (id in queryIds) idsToQueryListIndexes[id] = idx++
        val shuffledSet = LinkedHashSet<Long>(shuffleIds.size)
        shuffledSet.addAll(shuffleIds)

        // Keep common IDs (intersect)
        shuffledSet.retainAll(queryIdSet)

        // Isolate new IDs that have never been shuffled and append them at the end
        if (shuffledSet.size < queryIdSet.size) {
            queryIdSet.removeAll(shuffledSet)
            shuffledSet.addAll(queryIdSet)
        }
        shuffledList = shuffledSet.toList()
        observer = DataObserver { this.invalidate() }
        query.subscribe().onlyChanges().weak().observer(observer)
    }

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<T>) {
        val totalCount = query.count().toInt()
        if (totalCount == 0) {
            callback.onResult(emptyList(), 0, 0)
        } else {
            val position = computeInitialLoadPosition(params, totalCount)
            val loadSize = computeInitialLoadSize(params, position, totalCount)
            val list: List<T?> = this.loadRange(position, loadSize)
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

    private fun loadRange(startPosition: Int, loadCount: Int): List<T?> {
        val lazyList = query.findLazy()
        val maxPage =
            min((startPosition + loadCount).toDouble(), shuffledList.size.toDouble()).toInt()
        val result: MutableList<T?> = ArrayList()
        for (i in startPosition until maxPage) {
            val index = idsToQueryListIndexes[shuffledList[i]]
            if (index != null && index < lazyList.size) result.add(lazyList[index])
        }
        return result
    }


    class RandomDataSourceFactory<I> internal constructor(
        private val query: Query<I>,
        private val shuffleIds: List<Long>
    ) : Factory<Int, I>() {
        override fun create(): DataSource<Int, I> {
            return ObjectBoxRandomDataSource(query, shuffleIds)
        }
    }
}