package me.devsaki.hentoid.util;

import timber.log.Timber;

public class RandomSeed {
    private static final RandomSeed ourInstance = new RandomSeed();

    private double randomNumber;

    public static RandomSeed getInstance() {
        return ourInstance;
    }

    private RandomSeed() {
        renewSeed();
    }

    public double getRandomNumber() {
        return randomNumber;
    }

    public void renewSeed()
    {
        randomNumber = Math.random();
    }
}
