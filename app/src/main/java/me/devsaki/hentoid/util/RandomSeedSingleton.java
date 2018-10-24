package me.devsaki.hentoid.util;

public class RandomSeedSingleton {
    private static volatile RandomSeedSingleton instance = null;

    private double randomNumber;


    private RandomSeedSingleton() {
        renewSeed();
    }

    public static RandomSeedSingleton getInstance() {
        if (RandomSeedSingleton.instance == null) {
            synchronized(RandomSeedSingleton.class) {
                if (RandomSeedSingleton.instance == null) {
                    RandomSeedSingleton.instance = new RandomSeedSingleton();
                }
            }
        }
        return RandomSeedSingleton.instance;
    }

    public double getRandomNumber() {
        return randomNumber;
    }

    public void renewSeed() {
        randomNumber = Math.random();
    }
}
