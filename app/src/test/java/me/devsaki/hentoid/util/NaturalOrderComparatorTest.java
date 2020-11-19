package me.devsaki.hentoid.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NaturalOrderComparatorTest {

    @Test
    public void compare() {
        List<String> refVals = Stream.of("033_chr_0228_2d_r18", "034_chr_0228_2e_r18", "035_chr_0228_2f_r18", "036_chr_0229a_r18", "037_chr_0229b_r18", "038_chr_0229c_r18").collect(Collectors.toList());
        List<String> testVals = new ArrayList<>(refVals);

        testVals.sort(new NaturalOrderComparator());

        for (int i = 0; i < refVals.size(); i++)
            Assert.assertEquals(testVals.get(i), refVals.get(i));
    }
}