package me.devsaki.hentoid.util;

import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NaturalOrderComparatorTest {

    @Test
    public void compare0a() {
        List<String> refVals = Stream.of("0.3", "0.6", "0.7", "0.8", "0.9", "1.0", "1.0b", "1.0c", "1.1", "1.2", "1.3").collect(Collectors.toList());
        List<String> testVals = new ArrayList<>(refVals);

        testVals.sort(CaseInsensitiveSimpleNaturalComparator.getInstance());

        for (int i = 0; i < refVals.size(); i++)
            Assert.assertEquals(refVals.get(i), testVals.get(i));
    }

    @Test
    public void compare0b() {
        /* Original test set; only works with padler sort
        List<String> refVals = Stream.of("1-2", "1-02", "1-20", "10-20", "fred", "jane", "pic01",
                "pic2", "pic02", "pic02a", "pic3", "pic4", "pic 4 else", "pic 5", "pic 5", "pic05",
                "pic 5 something", "pic 6", "pic   7", "pic100", "pic100a", "pic120", "pic121",
                "pic02000", "tom", "x2-g8", "x2-y7", "x2-y08", "x8-y8").collect(Collectors.toList());
         */
        // Altered test set that work with Gpanther's algorithm, closer to Windows explorer's natural sort
        List<String> refVals = Stream.of("1-2", "1-02", "1-20", "10-20", "fred", "jane", "pic01",
                "pic2", "pic02", "pic02a", "pic3", "pic4", "pic05", "pic100", "pic100a", "pic120", "pic121",
                "pic02000", "pic 4 else", "pic 5", "pic 5", "pic 5 something", "pic 6", "pic   7",
                "tom", "x2-g8", "x2-y7", "x2-y08", "x8-y8").collect(Collectors.toList());
        List<String> testVals = new ArrayList<>(refVals);

        testVals.sort(CaseInsensitiveSimpleNaturalComparator.getInstance());

        for (int i = 0; i < refVals.size(); i++)
            Assert.assertEquals(refVals.get(i), testVals.get(i));
    }

    @Test
    public void compare1() {
        List<String> refVals = Stream.of("033_chr_0228_2d_r18", "034_chr_0228_2e_r18", "035_chr_0228_2f_r18", "036_chr_0229a_r18", "037_chr_0229b_r18", "038_chr_0229c_r18").collect(Collectors.toList());
        List<String> testVals = new ArrayList<>(refVals);

        testVals.sort(CaseInsensitiveSimpleNaturalComparator.getInstance());

        for (int i = 0; i < refVals.size(); i++)
            Assert.assertEquals(refVals.get(i), testVals.get(i));
    }

    @Test
    public void compare2() {
        List<String> refVals = Stream.of("1", "2", "10", "11", "20", "21", "100", "101").collect(Collectors.toList());
        List<String> testVals = new ArrayList<>(refVals);

        testVals.sort(CaseInsensitiveSimpleNaturalComparator.getInstance());

        for (int i = 0; i < refVals.size(); i++)
            Assert.assertEquals(refVals.get(i), testVals.get(i));
    }

    @Test
    public void compare3() {
        List<String> refVals = Stream.of("season1episode1", "season1episode10", "season02episode01", "season02episode2", "season02episode03", "season3episode1", "season3episode10").collect(Collectors.toList());
        List<String> testVals = new ArrayList<>(refVals);

        testVals.sort(CaseInsensitiveSimpleNaturalComparator.getInstance());

        for (int i = 0; i < refVals.size(); i++)
            Assert.assertEquals(refVals.get(i), testVals.get(i));
    }

    @Test
    public void compare4() {
        List<String> refVals = Stream.of("000.jpg", "001.jpg", "002.jpg", "003.jpg", "027r.jpg").collect(Collectors.toList());
        List<String> testVals = new ArrayList<>(refVals);

        testVals.sort(CaseInsensitiveSimpleNaturalComparator.getInstance());

        for (int i = 0; i < refVals.size(); i++)
            Assert.assertEquals(refVals.get(i), testVals.get(i));
    }
}