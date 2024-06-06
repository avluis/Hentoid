package me.devsaki.hentoid.util.string_similarity;

import java.util.Collections;
import org.apache.commons.collections4.map.HashedMap;
import java.util.Map;

import me.devsaki.hentoid.util.StringHelper;

/**
 * Abstract class for string similarities that rely on set operations (like
 * cosine similarity or jaccard index).
 * <p>
 * k-shingling is the operation of transforming a string (or text document) into
 * a set of n-grams, which can be used to measure the similarity between two
 * strings or documents.
 * <p>
 * Generally speaking, a k-gram is any sequence of k tokens. We use here the
 * definition from Leskovec, Rajaraman &amp; Ullman (2014), "Mining of Massive
 * Datasets", Cambridge University Press: Multiple subsequent spaces are
 * replaced by a single space, and a k-gram is a sequence of k characters.
 * <p>
 * Default value of k is 3. A good rule of thumb is to imagine that there are
 * only 20 characters and estimate the number of k-shingles as 20^k. For small
 * documents like e-mails, k = 5 is a recommended value. For large documents,
 * such as research articles, k = 9 is considered a safe choice.
 *
 * @author Thibault Debatty
 */
public abstract class ShingleBased {

    private static final int DEFAULT_K = 3;

    private final int k;

    /**
     * @param k
     * @throws IllegalArgumentException if k is &lt;= 0
     */
    public ShingleBased(final int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k should be positive!");
        }
        this.k = k;
    }

    /**
     *
     */
    ShingleBased() {
        this(DEFAULT_K);
    }

    /**
     * Return k, the length of k-shingles (aka n-grams).
     *
     * @return The length of k-shingles.
     */
    public final int getK() {
        return k;
    }

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
    public final Map<String, Integer> getProfile(final String string) {
        HashedMap<String, Integer> shingles = new HashedMap<String, Integer>();

        String string_no_space = StringHelper.cleanMultipleSpaces(string);
        for (int i = 0; i < (string_no_space.length() - k + 1); i++) {
            String shingle = string_no_space.substring(i, i + k);
            Integer old = shingles.get(shingle);
            if (old != null) {
                shingles.put(shingle, old + 1);
            } else {
                shingles.put(shingle, 1);
            }
        }

        return Collections.unmodifiableMap(shingles);
    }
}
