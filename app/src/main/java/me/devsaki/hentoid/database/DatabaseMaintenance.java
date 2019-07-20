package me.devsaki.hentoid.database;

import android.content.Context;
import android.util.Pair;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import timber.log.Timber;

public class DatabaseMaintenance {

    /**
     * Clean up and upgrade database
     * NB : Heavy operations; must be performed in the background to avoid ANR at startup
     */
    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    public static void performDatabaseHousekeeping(Context context) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);

        Timber.d("Content item(s) count: %s", db.countContentEntries());

        // Perform functional data updates
        performDatabaseCleanups(db);

        HentoidDB oldDb = HentoidDB.getInstance(context);
        // Perform technical data updates on the old database engine
        if (oldDb.countContentEntries() > 0) {
            oldDb.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
        }
    }

    private static void performDatabaseCleanups(ObjectBoxDB db) {
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

        // Update URLs from deprecated Pururin hosts
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
    }

    /**
     * Handles complex DB version updates at startup
     */
    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    public static void performOldDatabaseUpdate(HentoidDB db) {
        // Update all "storage_folder" fields in CONTENT table (mandatory) (since versionCode 44 / v1.2.2)
        List<Content> contents = db.selectContentEmptyFolder();
        if (contents != null && !contents.isEmpty()) {
            for (int i = 0; i < contents.size(); i++) {
                Content content = contents.get(i);
                content.setStorageFolder("/" + content.getSite().getDescription() + "/" + content.getOldUniqueSiteId()); // This line must use deprecated code, as it migrates it to newest version
                db.updateContentStorageFolder(content);
            }
        }

        // Migrate the old download queue (books in DOWNLOADING or PAUSED status) in the queue table (since versionCode 60 / v1.3.7)
        // Gets books that should be in the queue but aren't
        List<Integer> contentToMigrate = db.selectContentsForQueueMigration();

        if (!contentToMigrate.isEmpty()) {
            // Gets last index of the queue
            List<Pair<Integer, Integer>> queue = db.selectQueue();
            int lastIndex = 1;
            if (!queue.isEmpty()) {
                lastIndex = queue.get(queue.size() - 1).second + 1;
            }

            for (int i : contentToMigrate) {
                db.insertQueue(i, lastIndex++);
            }
        }
    }

    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    public static boolean hasToMigrate(Context context) {
        HentoidDB oldDb = HentoidDB.getInstance(context);
        return (oldDb.countContentEntries() > 0);
    }
}
