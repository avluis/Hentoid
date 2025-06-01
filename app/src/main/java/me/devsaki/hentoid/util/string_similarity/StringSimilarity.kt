package me.devsaki.hentoid.util.string_similarity

import java.io.Serializable

/**
 * @author Thibault Debatty
 */
interface StringSimilarity : Serializable {
    /**
     * Compute and return a measure of similarity between 2 strings.
     *
     * @param s1
     * @param s2
     * @return similarity (0 means both strings are completely different)
     */
    fun similarity(s1: String, s2: String): Double
}
