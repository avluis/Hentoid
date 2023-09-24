package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.firebase.FirebaseApp;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.string_similarity.Cosine;
import timber.log.Timber;

@RunWith(RobolectricTestRunner.class)
public class TextDupeDetectorTest {

    @BeforeClass
    public static void prepareTimber() {
        Timber.plant(new Timber.DebugTree());
    }

    @Before // Crashes when used inside @BeforeClass. Only valid way to use that is inside @Before
    public void prepareSupportTools() {
        FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext());
    }

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
        System.out.printf("%d lines loaded\n", vals1.size());

        float tolerance = 0.01f;
        int sensitivity = 0; // 0=loosely similar; 2=very similar
        boolean ignoreChapters = true;

        Cosine c = new Cosine();
        for (String s1 : vals1) {
            Content c1 = new Content().setTitle(s1);
            DuplicateHelper.DuplicateCandidate dc1 = new DuplicateHelper.DuplicateCandidate(c1, true, false, false, false, ignoreChapters, Long.MIN_VALUE);
            //String s1c = StringHelper.cleanup(s1);
            //Triple<String, Integer, Integer> s1cp = DuplicateHelper.Companion.sanitizeTitle(s1c);
            for (String s2 : vals1) {
                //if (s1 == s2) break;
                //noinspection StringEquality
                if (s1 == s2) continue; // Test _both_ combinations

                Content c2 = new Content().setTitle(s2);
                DuplicateHelper.DuplicateCandidate dc2 = new DuplicateHelper.DuplicateCandidate(c2, true, false, false, false, ignoreChapters, Long.MIN_VALUE);
                //String s2c = StringHelper.cleanup(s2);
                //Triple<String, Integer, Integer> s2cp = DuplicateHelper.Companion.sanitizeTitle(s2c);
                double score = DuplicateHelper.Companion.computeTitleScore(c, dc1, dc2, ignoreChapters, sensitivity);
                if (score > 0) {
                    System.out.printf("[%.4f] %s > %s\n", score, dc1.getTitleCleanup(), dc2.getTitleCleanup());
                    /*
                    double similarity1 = c.similarity(dc1.getTitleCleanup(), dc2.getTitleCleanup());
                    double similarity2 = c.similarity(dc1.getTitleNoDigits(), dc2.getTitleNoDigits());
                    double distance = similarity2 - similarity1;
                    System.out.printf("%s %s [%.4f - %.4f = %.4f ==> %.4f] %s%n", (distance < tolerance) ? "MATCH " : "CHAPTER ", dc1.getTitleCleanup(), similarity1, similarity2, distance, score, dc2.getTitleCleanup());
                    if (distance < tolerance)
                        System.out.printf("%s > %s%n", dc1.getTitleNoDigits(), dc2.getTitleNoDigits());
                     */
                }
            }
        }
        System.out.print("Done\n");
        System.out.flush();
    }
}