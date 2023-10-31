package me.devsaki.hentoid.database

import io.objectbox.internal.ReflectionCache
import io.objectbox.query.Query
import io.objectbox.query.QueryBuilder

fun <T> QueryBuilder<T>.safeFind(): List<T> {
    return this.build().safeFind()
}

fun <T> Query<T>.safeFind(): List<T> {
    this.use { return this.find() }
}

fun <T> QueryBuilder<T>.safeFindFirst(): T? {
    return this.build().safeFindFirst()
}

fun <T> Query<T>.safeFindFirst(): T? {
    this.use { return this.findFirst() }
}

fun <T> QueryBuilder<T>.safeFindIds(): LongArray {
    return this.build().safeFindIds()
}

fun <T> Query<T>.safeFindIds(): LongArray {
    this.use { return this.findIds() }
}

fun <T> QueryBuilder<T>.safeCount(): Long {
    return this.build().safeCount()
}

fun <T> Query<T>.safeCount(): Long {
    this.use { return this.count() }
}

fun <T> QueryBuilder<T>.safeRemove() {
    return this.build().safeRemove()
}

fun <T> Query<T>.safeRemove() {
    this.use { this.remove() }
}

// Inspired by ToMany.ensureBoxes
fun isDetached(entity: Any): Boolean {
    val boxStoreField = ReflectionCache.getInstance().getField(entity.javaClass, "__boxStore")
    return try {
        null == boxStoreField[entity]
    } catch (e: IllegalAccessException) {
        throw RuntimeException(e)
    }
}