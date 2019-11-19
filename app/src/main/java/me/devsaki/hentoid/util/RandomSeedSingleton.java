package me.devsaki.hentoid.util;

@SuppressWarnings("squid:S3077") // https://stackoverflow.com/questions/11639746/what-is-the-point-of-making-the-singleton-instance-volatile-while-using-double-l
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
