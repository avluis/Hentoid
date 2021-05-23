package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.util.string_similarity.Cosine;
import timber.log.Timber;

@RunWith(RobolectricTestRunner.class)
public class TextDupeDetectorTest {

    @Test
    public void displayDistances() {
        List<String> vals1 = new ArrayList<>();
        Context context = ApplicationProvider.getApplicationContext();
        try (InputStream is = context.getAssets().open("test_titles.txt"); BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                vals1.add(sCurrentLine);
            }
        } catch (Exception e) {
            Timber.e(e);
        }

        Assert.assertFalse(vals1.isEmpty());

        Cosine c = new Cosine();
        for (String s1 : vals1) {
            String s1c = StringHelper.cleanup(s1);
            String s1cp = DuplicateHelper.Companion.sanitizeTitle(s1c);
            for (String s2 : vals1) {
                if (s1 == s2) continue;
                String s2c = StringHelper.cleanup(s2);
                String s2cp = DuplicateHelper.Companion.sanitizeTitle(s2c);
                double score = DuplicateHelper.Companion.computeTitleScore(c, s1c, s1cp, s2c, s2cp, true, 0);
                if (score > 0) {
                    double similarity1 = c.similarity(s1c, s2c);
                    double similarity2 = c.similarity(s1cp, s2cp);
                    double distance = similarity2 - similarity1;
                    System.out.printf("%s %s [%.4f - %.4f = %.4f ==> %.4f] %s%n", (distance < 0.01) ? "MATCH " : "CHAPTER ", s1c, similarity1, similarity2, distance, score, s2c);
                    if (distance < 0.01)
                        System.out.printf("%s > %s%n", s1cp, s2cp);
                }
            }
        }
    }
}