package me.devsaki.hentoid.util;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("squid:S3077")
// https://stackoverflow.com/questions/11639746/what-is-the-point-of-making-the-singleton-instance-volatile-while-using-double-l
public class RandomSeedSingleton {
    private static volatile RandomSeedSingleton instance = null;

    private final Map<String, Long> seeds = new HashMap<>();

    
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

    public long getSeed(@NonNull String key) {
        if (!seeds.containsKey(key)) renewSeed(key);
        return seeds.get(key);
    }

    public void renewSeed(@NonNull String key) {
        seeds.put(key, Math.round(Math.random() * Long.MAX_VALUE));
    }
}
