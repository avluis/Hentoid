package me.devsaki.hentoid.util;

public class RandomSeedSingleton {
    private static volatile RandomSeedSingleton instance = null;

    private long seed;


    private RandomSeedSingleton() {
        renewSeed();
    }

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

    public long getSeed() {
        return seed;
    }

    public void renewSeed() {
        seed = Math.round(Math.random() * Long.MAX_VALUE);
    }
}
