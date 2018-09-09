package me.devsaki.hentoid.util;

public class IllegalTags {

    private static final String[] ILLEGAL_TAGS = { "loli", "shota", "toddler", "baby" };

    public static boolean isIllegal(String s)
    {
        for (String tag : ILLEGAL_TAGS) if (s.toLowerCase().contains(tag)) return true;
        return false;
    }
}
