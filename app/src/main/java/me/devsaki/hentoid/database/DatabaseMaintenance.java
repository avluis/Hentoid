package me.devsaki.hentoid.database;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import timber.log.Timber;

public class DatabaseMaintenance {

    private DatabaseMaintenance() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Clean up and upgrade database
     * NB : Heavy operations; must be performed in the background to avoid ANR at startup
     */
    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    public static void performDatabaseHousekeeping(@NonNull Context context) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);

        Timber.d("Content item(s) count: %s", db.countContentEntries());

        // Perform functional data updates
        performDatabaseCleanups(db);
        db.closeThreadResources();
    }

    private static void performDatabaseCleanups(@NonNull ObjectBoxDB db) {
        // Set items that were being downloaded in previous session as paused
        Timber.i("Updating queue status : start");
        db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
        Timber.i("Updating queue status : done");

        // Add back in the queue isolated DOWNLOADING or PAUSED books that aren't in the queue (since version code 106 / v1.8.0)
        Timber.i("Moving back isolated items to queue : start");
        List<Content> contents = db.selectContentByStatus(StatusContent.PAUSED);
        List<Content> queueContents = db.selectQueueContents();
        contents.removeAll(queueContents);
        if (!contents.isEmpty()) {
            int queueMaxPos = (int) db.selectMaxQueueOrder();
            for (Content c : contents) db.insertQueue(c.getId(), ++queueMaxPos);
        }
        Timber.i("Moving back isolated items to queue : done");

        // Clear temporary books created from browsing a book page without downloading it (since versionCode 60 / v1.3.7)
        Timber.i("Clearing temporary books : start");
        contents = db.selectContentByStatus(StatusContent.SAVED);
        Timber.i("Clearing temporary books : %s books detected", contents.size());
        for (Content c : contents) db.deleteContent(c);
        Timber.i("Clearing temporary books : done");

        // Update URLs from deprecated Pururin image hosts
        Timber.i("Upgrading Pururin image hosts : start");
        contents = db.selectContentWithOldPururinHost();
        Timber.i("Upgrading Pururin image hosts : %s books detected", contents.size());
        for (Content c : contents) {
            c.setCoverImageUrl(c.getCoverImageUrl().replace("api.pururin.io/images/", "cdn.pururin.io/assets/images/data/"));
            if (c.getImageFiles() != null)
                for (ImageFile i : c.getImageFiles()) {
                    db.updateImageFileUrl(i.setUrl(i.getUrl().replace("api.pururin.io/images/", "cdn.pururin.io/assets/images/data/")));
                }
            db.insertContent(c);
        }
        Timber.i("Upgrading Pururin image hosts : done");

        // Update URLs from deprecated Tsumino image covers
        Timber.i("Upgrading Tsumino covers : start");
        contents = db.selectContentWithOldTsuminoCovers();
        Timber.i("Upgrading Tsumino covers : %s books detected", contents.size());
        for (Content c : contents) {
            String url = c.getCoverImageUrl().replace("www.tsumino.com/Image/Thumb", "content.tsumino.com/thumbs");
            if (!url.endsWith("/1")) url += "/1";
            c.setCoverImageUrl(url);
            db.insertContent(c);
        }
        Timber.i("Upgrading Tsumino covers : done");

        // Compute missing downloaded Content size according to underlying ImageFile sizes
        Timber.i("Computing downloaded content size : start");
        contents = db.selectDownloadedContentWithNoSize();
        Timber.i("Computing downloaded content size : %s books detected", contents.size());
        for (Content c : contents) {
            c.computeSize();
            db.insertContent(c);
        }
        Timber.i("Computing downloaded content size : done");
    }
}
