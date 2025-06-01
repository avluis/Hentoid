package me.devsaki.hentoid.util.string_similarity

import me.devsaki.hentoid.util.cleanMultipleSpaces
import java.util.Collections

private const val DEFAULT_K = 3

/**
 * Abstract class for string similarities that rely on set operations (like
 * cosine similarity or jaccard index).
 *
 *
 * k-shingling is the operation of transforming a string (or text document) into
 * a set of n-grams, which can be used to measure the similarity between two
 * strings or documents.
 *
 *
 * Generally speaking, a k-gram is any sequence of k tokens. We use here the
 * definition from Leskovec, Rajaraman &amp; Ullman (2014), "Mining of Massive
 * Datasets", Cambridge University Press: Multiple subsequent spaces are
 * replaced by a single space, and a k-gram is a sequence of k characters.
 *
 *
 * Default value of k is 3. A good rule of thumb is to imagine that there are
 * only 20 characters and estimate the number of k-shingles as 20^k. For small
 * documents like e-mails, k = 5 is a recommended value. For large documents,
 * such as research articles, k = 9 is considered a safe choice.
 *
 * @author Thibault Debatty
 */
abstract class ShingleBased(k: Int) {
    /**
     * Return k, the length of k-shingles (aka n-grams).
     *
     * @return The length of k-shingles.
     */
    val k: Int

    /**
     * @param k
     * @throws IllegalArgumentException if k is &lt;= 0
     */
    init {
        require(k > 0) { "k should be positive!" }
        this.k = k
    }

    /**
     *
     */
    internal constructor() : this(DEFAULT_K)

    /**
     * Compute and return the profile of s, as defined by Ukkonen "Approximate
     * string-matching with q-grams and maximal matches".
     * https://www.cs.helsinki.fi/u/ukkonen/TCS92.pdf The profile is the number
     * of occurrences of k-shingles, and is used to compute q-gram similarity,
     * Jaccard index, etc. Pay attention: the memory requirement of the profile
     * can be up to k * size of the string
     *
     * @param string
     * @return the profile of this string, as an unmodifiable Map
     */
    fun getProfile(string: String): MutableMap<String, Int> {
        val shingles = HashMap<String, Int>()

        val stringNoSpace = cleanMultipleSpaces(string)
        for (i in 0..<(stringNoSpace.length - k + 1)) {
            val shingle = stringNoSpace.substring(i, i + k)
            val old = shingles.get(shingle)
            if (old != null) {
                shingles.put(shingle, old + 1)
            } else {
                shingles.put(shingle, 1)
            }
        }

        return Collections.unmodifiableMap<String, Int>(shingles)
    }
}
