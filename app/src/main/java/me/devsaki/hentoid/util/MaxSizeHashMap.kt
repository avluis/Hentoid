package me.devsaki.hentoid.util

class MaxSizeHashMap<K, V>(private val maxSize: Int = 0) : LinkedHashMap<K, V>() {

    override fun removeEldestEntry(eldest: Map.Entry<K, V>?): Boolean {
        return size > maxSize
    }
}