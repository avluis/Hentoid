package me.devsaki.hentoid.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.ObservableEmitter;
import me.devsaki.hentoid.util.ImagePHash;
import timber.log.Timber;

public class AppStartupDev {

    private AppStartupDev() {
        throw new IllegalStateException("Utility class");
    }


    public static void testImg(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        Timber.i("Test img : start");

        int resolution = 48;
        List<Long> hashes1 = new ArrayList<>();
        List<Long> hashes2 = new ArrayList<>();

        try {
            ImagePHash hash = new ImagePHash(resolution, 8);
            //Bitmap.Config bitmapConfig = Bitmap.Config.ARGB_8888;
            Bitmap.Config bitmapConfig = Bitmap.Config.RGB_565;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = bitmapConfig;

            String[] set1 = context.getAssets().list("imageSet1");
            if (null == set1) return;

            for (String s : set1) {
                try (InputStream is = context.getAssets().open("imageSet1/" + s)) {
                    Bitmap b = BitmapFactory.decodeStream(is, null, options);
                    long hashStr = hash.calcPHash(b);
                    hashes1.add(hashStr);
                    System.out.println(s + " : " + hashStr);
                } catch (Exception e) {
                    Timber.e(e);
                    System.out.println(e.getMessage());
                    System.out.println(s + " ko");
                }
            }

            String[] set2 = context.getAssets().list("imageSet2");
            if (null == set2) return;

            for (String s : set2) {
                try (InputStream is = context.getAssets().open("imageSet2/" + s)) {
                    Bitmap b = BitmapFactory.decodeStream(is, null, options);
                    long hashStr = hash.calcPHash(b);
                    hashes2.add(hashStr);
                    System.out.println(s + " : " + hashStr);
                } catch (Exception e) {
                    Timber.e(e);
                    System.out.println(e.getMessage());
                    System.out.println(s + " ko");
                }
            }

            for (int i = 0; i < 10; i++)
                runThresholdTest(hashes1, hashes2, Arrays.asList(set1), Arrays.asList(set2), 0.7f + (i / 100f));

        } catch (IOException e) {
            Timber.e(e);
        } finally {
            emitter.onComplete();
        }
        Timber.i("Test img : done");
    }

    private static void runThresholdTest(List<Long> hashes1, List<Long> hashes2, List<String> vals1, List<String> vals2, float threshold) {
        int nbSuccess = 0;
        // Cross-comparisons between set 1
        for (int i = 0; i < hashes1.size(); i++) {
            for (int j = 0; j < hashes1.size(); j++) {
//                if (i == j) continue;
                double sim = ImagePHash.similarity(hashes1.get(i), hashes1.get(j));
                if (sim > threshold) {
                    nbSuccess++;
                    Timber.d("%s =%s= %s", vals1.get(i), sim, vals1.get(j));
                }
            }
        }
        Timber.i(">> SET 1 nbCrossSuccess for %s : %s", threshold, nbSuccess * 100f / (vals1.size() * vals1.size()));

        nbSuccess = 0;
        // Cross-comparisons between set 2
        for (int i = 0; i < hashes2.size(); i++) {
            for (int j = 0; j < hashes2.size(); j++) {
//                if (i == j) continue;
                double sim = ImagePHash.similarity(hashes2.get(i), hashes2.get(j));
                if (sim > threshold) {
                    nbSuccess++;
                    Timber.d("%s =%s= %s", vals2.get(i), sim, vals2.get(j));
                }
            }
        }
        Timber.i(">> SET 2 nbCrossSuccess for %s : %s", threshold, nbSuccess * 100f / (vals2.size() * vals2.size()));

        nbSuccess = 0;
        // Comparisons between sets 1 & 2
        for (int i = 0; i < hashes1.size(); i++) {
            for (int j = 0; j < hashes2.size(); j++) {
                double sim = ImagePHash.similarity(hashes1.get(i), hashes2.get(j));
                if (sim > threshold) {
                    nbSuccess++;
                    Timber.d("%s =%s= %s", vals1.get(i), sim, vals2.get(j));
                }
            }
        }
        Timber.i(">> xSET nbSuccess for %s : %s", threshold, nbSuccess * 100f / (vals1.size() * vals2.size()));
    }
}
