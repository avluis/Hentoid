package me.devsaki.hentoid.util;

import java.util.Comparator;

public final class NaturalOrderComparator<T extends CharSequence>
        extends AbstractNaturalOrderComparator<T> implements Comparator<T> {
    @SuppressWarnings("rawtypes")
    private static final Comparator INSTANCE = new NaturalOrderComparator();

    private NaturalOrderComparator() {
        // to be instantiated only internally
    }

    @Override
    int compareChars(char c1, char c2) {
        return Character.toLowerCase(c1) - Character.toLowerCase(c2);
    }

    @SuppressWarnings("unchecked")
    public static <T extends CharSequence> Comparator<T> getInstance() {
        return INSTANCE;
    }
}