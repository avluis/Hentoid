package me.devsaki.hentoid.db;

import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import timber.log.Timber;

/**
 * Created by neko on 15/06/2015.
 * Basic database test case
 * </p>
 * TODO: Update with new Testing Support Library (and possibly ContentV2)
 */
public class TestHentoidDB extends AndroidTestCase {

    boolean locker1, locker2, locker3, locker4;

    public void testLock() {
        List<Content> contents = generateRandomContent();

        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
        HentoidDB db = HentoidDB.getInstance(context);
        db.insertContents(contents.toArray(new Content[contents.size()]));
        try {
            new Thread(() -> {
                try {
                    RenamingDelegatingContext context1 = new RenamingDelegatingContext(getContext(), "test_");
                    HentoidDB db1 = HentoidDB.getInstance(context1);
                    for (int i = 0; i < 100; i++) {
                        List<Content> contents1 = generateRandomContent();
                        db1.insertContents(contents1.toArray(new Content[contents1.size()]));
                    }
                } catch (Exception ex) {
                    Timber.e(ex, "Error");
                }
                locker1 = true;
            }).start();
            new Thread(() -> {
                try {
                    RenamingDelegatingContext context12 = new RenamingDelegatingContext(getContext(), "test_");
                    HentoidDB db12 = HentoidDB.getInstance(context12);
                    for (int i = 0; i < 100; i++) {
                        List<Content> contents12 = generateRandomContent();
                        db12.insertContents(contents12.toArray(new Content[contents12.size()]));
                    }
                } catch (Exception ex) {
                    Timber.e(ex, "Error");
                }
                locker2 = true;
            }).start();
            new Thread(() -> {
                try {
                    RenamingDelegatingContext context13 = new RenamingDelegatingContext(getContext(), "test_");
                    HentoidDB db13 = HentoidDB.getInstance(context13);
                    for (int i = 0; i < 100; i++) {
//                        db13.selectContentByQuery("", "", 1, 10, Collections.emptyList(), Collections.emptyList(), 0);
                    }
                } catch (Exception ex) {
                    Timber.e(ex, "Error");
                }
                locker3 = true;
            }).start();
            new Thread(() -> {
                try {
                    RenamingDelegatingContext context14 = new RenamingDelegatingContext(getContext(), "test_");
                    HentoidDB db14 = HentoidDB.getInstance(context14);
                    for (int i = 0; i < 100; i++) {
//                        db14.selectContentByStatus(StatusContent.DOWNLOADED);
                    }
                } catch (Exception ex) {
                    Timber.e(ex, "Error");
                }
                locker4 = true;
            }).start();
            //noinspection StatementWithEmptyBody
            while (!(locker1 && locker2 && locker3 && locker4)) ;
            Timber.i("DB Lock: Success");
        } catch (Exception ex) {
            Timber.e(ex, "Error");
        }
    }

    private List<Content> generateRandomContent() {
        List<Content> contents = new ArrayList<>();
        Random randomGenerator = new Random();
        for (int i = 0; i < 10; i++) {
            int k = randomGenerator.nextInt();
            AttributeMap attributeMap = new AttributeMap();
            for (AttributeType type : AttributeType.values()) {
                for (int j = 0; j < 10; j++) {
                    int l = randomGenerator.nextInt();
                    attributeMap.add(new Attribute()
                            .setUrl("" + l)
                            .setName("n" + l)
                            .setType(type));
                }
            }
            contents.add(new Content()
                    .setAttributes(attributeMap)
                    .setUrl("/doujinshi/u" + k)
                    .setCoverImageUrl("c" + k)
                    .setDownloadDate(1000 * k)
                    .setPercent(10.0 * k)
                    .setQtyPages(k * 12)
                    .setTitle("t " + k)
                    .setStatus(StatusContent.DOWNLOADED)
                    .setUploadDate(k * 2000));
        }

        return contents;
    }
}
