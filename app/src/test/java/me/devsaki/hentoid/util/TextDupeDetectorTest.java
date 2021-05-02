package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import info.debatty.java.stringsimilarity.Cosine;
import timber.log.Timber;

@RunWith(RobolectricTestRunner.class)
public class TextDupeDetectorTest {

    @Test
    public void displayDistances() {
        /*
        Cosine c = new Cosine();
        System.out.println("COSINE");
        System.out.println(c.similarity(vals1.get(0), vals1.get(1)) + "");
        System.out.println(c.similarity(vals2.get(0), vals2.get(1)) + "");

        Kimodebu Ossan no Ore ga Namaiki Ojou-sama o Saimin NTR shite mita | 좆돼지 아재인 내가 싸가지없는 아가씨를 최면 NTR해 봤다
0.8168499661773069
Kimodebu Ossan no Ore ga Namaiki Ojou-sama o Saimin NTR shite mita


        SorensenDice s = new SorensenDice();
        System.out.println("SORENSEN DICE");
        System.out.println(s.similarity(vals1.get(0), vals1.get(1)) + "");
        System.out.println(s.similarity(vals2.get(0), vals2.get(1)) + "");

         */

        /*
        Jaccard j = new Jaccard();
        System.out.println("JACCARD");
        System.out.println(j.similarity(vals1.get(0), vals1.get(1)) + "");
        System.out.println(j.similarity(vals2.get(0), vals2.get(1)) + "");

        RatcliffObershelp ro = new RatcliffObershelp();
        System.out.println("RATCLIFF-OBERSHELP");
        System.out.println(ro.similarity(vals1.get(0), vals1.get(1)) + "");
        System.out.println(ro.similarity(vals2.get(0), vals2.get(1)) + "");

         */

        List<String> vals1 = new ArrayList<>();
        Context context = ApplicationProvider.getApplicationContext();
        try (InputStream is = context.getAssets().open("titles.txt"); BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                vals1.add(sCurrentLine);
            }
        } catch (Exception e) {
            Timber.e(e);
        }

        Cosine c = new Cosine();
        //SorensenDice c = new SorensenDice();
        for (String s1 : vals1) {
            String s1c = StringHelper.cleanup(s1);
            String s1cp = StringHelper.removeDigits(s1c);
            for (String s2 : vals1) {
                if (s1 == s2) continue;
                String s2c = StringHelper.cleanup(s2);
                double similarity = c.similarity(s1c, s2c);
                if (similarity > 0.8) {
                    String s2cp = StringHelper.removeDigits(s2c);
                    double similarity2 = c.similarity(s1cp, s2cp);
                    if (similarity2 - similarity < 0.02 && !s1cp.equals(s2cp)) {
                        System.out.println(s1c);
//                        System.out.println(s1cp);
                        System.out.println(similarity);
                        System.out.println(s2c);
//                        System.out.println(s2cp);
                        System.out.println("");
                    }
                    break;
                }
            }
        }
    }
}