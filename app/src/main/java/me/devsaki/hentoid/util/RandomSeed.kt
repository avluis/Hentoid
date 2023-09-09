package me.devsaki.hentoid.util

import kotlin.math.roundToLong

/**
 * Singleton used to manage RNG seed generation app-wide
 */
object RandomSeed {
    private val seeds: MutableMap<String, Long> = HashMap()

    /**
     * Get the seed for the given key
     * If no call to renewSeed has been made since the last call to getSeed,
     * returned value will remain identical
     *
     * @param key Unique key to get the seed for
     * @return Seed corresponding to the given key
     */
    fun getSeed(key: String): Long {
        var seed = seeds[key]
        if (null == seed) seed = renewSeed(key)
        return seed
    }

    /**
     * Initialize or renew the seed for the given key
     *
     * @param key Unique key to generate the seed for
     */
    fun renewSeed(key: String): Long {
        val result = (Math.random() * Long.MAX_VALUE).roundToLong()
        seeds[key] = result
        return result
    }
}