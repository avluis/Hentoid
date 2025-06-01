package me.devsaki.hentoid.util.string_similarity

import kotlin.math.sqrt

class Cosine : ShingleBased, StringSimilarity {
    /**
     * Implements Cosine Similarity between strings. The strings are first
     * transformed in vectors of occurrences of k-shingles (sequences of k
     * characters). In this n-dimensional space, the similarity between the two
     * strings is the cosine of their respective vectors.
     *
     * @param k
     */
    constructor(k: Int) : super(k)

    /**
     * Implements Cosine Similarity between strings. The strings are first
     * transformed in vectors of occurrences of k-shingles (sequences of k
     * characters). In this n-dimensional space, the similarity between the two
     * strings is the cosine of their respective vectors. Default k is 3.
     */
    constructor() : super()

    /**
     * Compute the cosine similarity between strings.
     *
     * @param s1 The first string to compare.
     * @param s2 The second string to compare.
     * @return The cosine similarity in the range [0, 1]
     * @throws NullPointerException if s1 or s2 is null.
     */
    override fun similarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0

        if (s1.length < k || s2.length < k) return 0.0

        val profile1 = getProfile(s1)
        val profile2 = getProfile(s2)

        return (dotProduct(profile1, profile2)
                / (norm(profile1) * norm(profile2)))
    }

    /**
     * Return 1.0 - similarity.
     *
     * @param s1 The first string to compare.
     * @param s2 The second string to compare.
     * @return 1.0 - the cosine similarity in the range [0, 1]
     * @throws NullPointerException if s1 or s2 is null.
     */
    fun distance(s1: String, s2: String): Double {
        return 1.0 - similarity(s1, s2)
    }

    /**
     * Compute similarity between precomputed profiles.
     *
     * @param profile1
     * @param profile2
     * @return
     */
    fun similarity(
        profile1: MutableMap<String, Int>,
        profile2: MutableMap<String, Int>
    ): Double {
        return (dotProduct(profile1, profile2) / (norm(profile1) * norm(profile2)))
    }

    companion object {
        /**
         * Compute the norm L2 : sqrt(Sum_i( v_i²)).
         *
         * @param profile
         * @return L2 norm
         */
        private fun norm(profile: MutableMap<String, Int>): Double {
            var agg = 0.0

            for (entry in profile.entries) {
                agg += 1.0 * entry.value * entry.value
            }

            return sqrt(agg)
        }

        private fun dotProduct(
            profile1: MutableMap<String, Int>,
            profile2: MutableMap<String, Int>
        ): Double {
            // Loop over the smallest map
            var smallProfile: MutableMap<String, Int> = profile2
            var largeProfile: MutableMap<String, Int> = profile1
            if (profile1.size < profile2.size) {
                smallProfile = profile1
                largeProfile = profile2
            }

            var agg = 0.0
            for (entry in smallProfile.entries) {
                val i = largeProfile[entry.key]
                if (i == null) continue
                agg += 1.0 * entry.value * i
            }

            return agg
        }
    }
}
