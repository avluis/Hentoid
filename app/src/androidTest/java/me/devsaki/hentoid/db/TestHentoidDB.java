package me.devsaki.hentoid.db;

import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by neko on 15/06/2015.
 * Basic database test case
 */
public class TestHentoidDB extends AndroidTestCase {
    private static final String TAG = LogHelper.makeLogTag(TestHentoidDB.class);

    boolean locker1, locker2, locker3, locker4;

    public void testLock() {
        List<Content> contents = generateRandomContent();

        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
        HentoidDB db = HentoidDB.getInstance(context);
        db.insertContents(contents.toArray(new Content[contents.size()]));
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
                        HentoidDB db = HentoidDB.getInstance(context);
                        for (int i = 0; i < 100; i++) {
                            List<Content> contents = generateRandomContent();
                            db.insertContents(contents.toArray(new Content[contents.size()]));
                        }
                    } catch (Exception ex) {
                        LogHelper.e(TAG, "Error: ", ex);
                    }
                    locker1 = true;
                }
            }).start();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
                        HentoidDB db = HentoidDB.getInstance(context);
                        for (int i = 0; i < 100; i++) {
                            List<Content> contents = generateRandomContent();
                            db.insertContents(contents.toArray(new Content[contents.size()]));
                        }
                    } catch (Exception ex) {
                        LogHelper.e(TAG, "Error: ", ex);
                    }
                    locker2 = true;
                }
            }).start();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
                        HentoidDB db = HentoidDB.getInstance(context);
                        for (int i = 0; i < 100; i++) {
                            db.selectContentByQuery("", 1, 10, false);
                        }
                    } catch (Exception ex) {
                        LogHelper.e(TAG, "Error: ", ex);
                    }
                    locker3 = true;
                }
            }).start();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
                        HentoidDB db = HentoidDB.getInstance(context);
                        for (int i = 0; i < 100; i++) {
                            db.selectContentByStatus(StatusContent.DOWNLOADED);
                        }
                    } catch (Exception ex) {
                        LogHelper.e(TAG, "Error: ", ex);
                    }
                    locker4 = true;
                }
            }).start();
            //noinspection StatementWithEmptyBody
            while (!(locker1 && locker2 && locker3 && locker4)) ;
            LogHelper.i(TAG, "DB Lock: Success");
        } catch (Exception ex) {
            LogHelper.e(TAG, "Error: ", ex);
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
