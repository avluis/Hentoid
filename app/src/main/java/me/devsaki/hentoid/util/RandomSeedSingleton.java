package me.devsaki.hentoid.util;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton used to manage RNG seed generation app-wide
 * https://stackoverflow.com/questions/11639746/what-is-the-point-of-making-the-singleton-instance-volatile-while-using-double-l
 */
@SuppressWarnings("squid:S3077")
public class RandomSeedSingleton {
    private static volatile RandomSeedSingleton instance = null;

    private final Map<String, Long> seeds = new HashMap<>();

    /**
     * Get the singleton instance
     *
     * @return Singleton instance
     */
    public static RandomSeedSingleton getInstance() {
        if (RandomSeedSingleton.instance == null) {
            synchronized (RandomSeedSingleton.class) {
                if (RandomSeedSingleton.instance == null) {
                    RandomSeedSingleton.instance = new RandomSeedSingleton();
                }
            }
        }
        return RandomSeedSingleton.instance;
    }

    /**
     * Get the seed for the given key
     * If no call to renewSeed has been made since the last call to getSeed,
     * returned value will remain identical
     *
     * @param key Unique key to get the seed for
     * @return Seed corresponding to the given key
     */
    public long getSeed(@NonNull String key) {
        Long seed = seeds.get(key);
        if (null == seed) seed = renewSeed(key);
        return seed;
    }

    /**
     * Initialize or renew the seed for the given key
     *
     * @param key Unique key to generate the seed for
     */
    public long renewSeed(@NonNull String key) {
        long result = Math.round(Math.random() * Long.MAX_VALUE);
        seeds.put(key, result);
        return result;
    }
}
