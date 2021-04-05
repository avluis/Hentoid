package me.devsaki.hentoid.database;

import android.content.Context;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.tuple.ImmutableTriple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.objectbox.query.Query;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.functions.BiConsumer;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

public class DatabaseMaintenance {

    private DatabaseMaintenance() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Clean up and upgrade database
     * NB : Heavy operations; must be performed in the background to avoid ANR at startup
     */
    public static List<Observable<Float>> getPreLaunchCleanupTasks(@NonNull final Context context) {
        List<Observable<Float>> result = new ArrayList<>();
        result.add(createObservableFrom(context, DatabaseMaintenance::cleanContent));
        result.add(createObservableFrom(context, DatabaseMaintenance::clearTempContent));
        result.add(createObservableFrom(context, DatabaseMaintenance::cleanPropertiesOneShot1));
        result.add(createObservableFrom(context, DatabaseMaintenance::cleanPropertiesOneShot2));
        result.add(createObservableFrom(context, DatabaseMaintenance::computeContentSize));
        result.add(createObservableFrom(context, DatabaseMaintenance::createGroups));
        result.add(createObservableFrom(context, DatabaseMaintenance::computeReadingProgress));
        return result;
    }

    public static List<Observable<Float>> getPostLaunchCleanupTasks(@NonNull final Context context) {
        List<Observable<Float>> result = new ArrayList<>();
        result.add(createObservableFrom(context, DatabaseMaintenance::cleanBookmarksOneShot));
        result.add(createObservableFrom(context, DatabaseMaintenance::cleanOrphanAttributes));
        return result;
    }

    private static Observable<Float> createObservableFrom(@NonNull final Context context, BiConsumer<Context, ObservableEmitter<Float>> function) {
        return Observable.create(emitter -> function.accept(context, emitter));
    }

    private static void cleanContent(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Set items that were being downloaded in previous session as paused
            Timber.i("Updating queue status : start");
            db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
            Timber.i("Updating queue status : done");

            // Unflag all books marked for deletion
            Timber.i("Unflag books : start");
            long[] ids = db.selectAllFlaggedBooksQ().findIds();
            Timber.i("Unflag books : %s books detected", ids.length);
            db.flagContentById(ids, false);
            Timber.i("Unflag books : done");

            // Unflag all books signaled as being deleted
            Timber.i("Unmark books as being deleted : start");
            ids = db.selectAllMarkedBooksQ().findIds();
            Timber.i("Unmark books as being deleted : %s books detected", ids.length);
            db.markContentById(ids, false);
            Timber.i("Unmark books as being deleted : done");

            // Add back in the queue isolated DOWNLOADING or PAUSED books that aren't in the queue (since version code 106 / v1.8.0)
            Timber.i("Moving back isolated items to queue : start");
            List<Content> contents = db.selectContentByStatus(StatusContent.PAUSED);
            List<Content> queueContents = db.selectQueueContents();
            contents.removeAll(queueContents);
            if (!contents.isEmpty()) {
                int queueMaxPos = (int) db.selectMaxQueueOrder();
                int max = contents.size();
                float pos = 1;
                for (Content c : contents) {
                    db.insertQueue(c.getId(), ++queueMaxPos);
                    emitter.onNext(pos++ / max);
                }
            }
            Timber.i("Moving back isolated items to queue : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }

    private static void clearTempContent(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Clear temporary books created from browsing a book page without downloading it (since versionCode 60 / v1.3.7)
            Timber.i("Clearing temporary books : start");
            List<Content> contents = db.selectContentByStatus(StatusContent.SAVED);
            Timber.i("Clearing temporary books : %s books detected", contents.size());
            int max = contents.size();
            float pos = 1;
            for (Content c : contents) {
                db.deleteContentById(c.getId(), false);
                emitter.onNext(pos++ / max);
            }
            Timber.i("Clearing temporary books : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }

    private static void cleanPropertiesOneShot1(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Update URLs from deprecated Pururin image hosts
            Timber.i("Upgrading Pururin image hosts : start");
            List<Content> contents = db.selectContentWithOldPururinHost();
            Timber.i("Upgrading Pururin image hosts : %s books detected", contents.size());
            int max = contents.size();
            float pos = 1;
            for (Content c : contents) {
                c.setCoverImageUrl(c.getCoverImageUrl().replace("api.pururin.io/images/", "cdn.pururin.io/assets/images/data/"));
                if (c.getImageFiles() != null)
                    for (ImageFile i : c.getImageFiles()) {
                        db.updateImageFileUrl(i.setUrl(i.getUrl().replace("api.pururin.io/images/", "cdn.pururin.io/assets/images/data/")));
                    }
                db.insertContent(c);
                emitter.onNext(pos++ / max);
            }
            Timber.i("Upgrading Pururin image hosts : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }

    private static void cleanPropertiesOneShot2(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Update URLs from deprecated Tsumino image covers
            Timber.i("Upgrading Tsumino covers : start");
            List<Content> contents = db.selectContentWithOldTsuminoCovers();
            Timber.i("Upgrading Tsumino covers : %s books detected", contents.size());
            int max = contents.size();
            float pos = 1;
            for (Content c : contents) {
                String url = c.getCoverImageUrl().replace("www.tsumino.com/Image/Thumb", "content.tsumino.com/thumbs");
                if (!url.endsWith("/1")) url += "/1";
                c.setCoverImageUrl(url);
                db.insertContent(c);
                emitter.onNext(pos++ / max);
            }
            Timber.i("Upgrading Tsumino covers : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }

    private static void cleanBookmarksOneShot(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Detect duplicate bookmarks (host/someurl and host/someurl/)
            Timber.i("Detecting duplicate bookmarks : start");
            Query<SiteBookmark> contents = db.selectAllDuplicateBookmarks();
            Timber.i("Detecting duplicate bookmarks : %d favourites detected", contents.count());
            contents.remove();
            Timber.i("Detecting duplicate bookmarks : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }

    private static void computeContentSize(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Compute missing downloaded Content size according to underlying ImageFile sizes
            Timber.i("Computing downloaded content size : start");
            List<Content> contents = db.selectDownloadedContentWithNoSize();
            Timber.i("Computing downloaded content size : %s books detected", contents.size());
            int max = contents.size();
            float pos = 1;
            for (Content c : contents) {
                c.computeSize();
                db.insertContent(c);
                emitter.onNext(pos++ / max);
            }
            Timber.i("Computing downloaded content size : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }

    private static void createGroups(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Compute missing downloaded Content size according to underlying ImageFile sizes
            Timber.i("Create non-existing groupings : start");
            List<Grouping> groupingsToProcess = new ArrayList<>();
            for (Grouping grouping : new Grouping[]{Grouping.ARTIST, Grouping.DL_DATE, Grouping.CUSTOM})
                if (0 == db.countGroupsFor(grouping)) groupingsToProcess.add(grouping);

            Timber.i("Create non-existing groupings : %s non-existing groupings detected", groupingsToProcess.size());
            int bookInsertCount = 0;
            List<ImmutableTriple<Group, Attribute, List<Content>>> toInsert = new ArrayList<>();
            for (Grouping g : groupingsToProcess) {
                if (g.equals(Grouping.ARTIST)) {
                    List<Attribute> artists = db.selectAvailableAttributes(AttributeType.ARTIST, null, null, false, Preferences.Constant.ORDER_ATTRIBUTES_ALPHABETIC, 0, 0);
                    artists.addAll(db.selectAvailableAttributes(AttributeType.CIRCLE, null, null, false, Preferences.Constant.ORDER_ATTRIBUTES_ALPHABETIC, 0, 0));
                    int order = 1;
                    for (Attribute a : artists) {
                        Group group = new Group(Grouping.ARTIST, a.getName(), order++);
                        group.setSubtype(a.getType().equals(AttributeType.ARTIST) ? Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS : Preferences.Constant.ARTIST_GROUP_VISIBILITY_GROUPS);
                        if (!a.contents.isEmpty())
                            group.picture.setTarget(a.contents.get(0).getCover());
                        bookInsertCount += a.contents.size();

                        toInsert.add(new ImmutableTriple<>(group, a, a.contents));
                    }
                } else if (g.equals(Grouping.DL_DATE)) {
                    Group group = new Group(Grouping.DL_DATE, "Today", 1);
                    group.propertyMin = 0;
                    group.propertyMax = 1;
                    toInsert.add(new ImmutableTriple<>(group, null, Collections.emptyList()));
                    group = new Group(Grouping.DL_DATE, "Last 7 days", 2);
                    group.propertyMin = 1;
                    group.propertyMax = 8;
                    toInsert.add(new ImmutableTriple<>(group, null, Collections.emptyList()));
                    group = new Group(Grouping.DL_DATE, "Last 30 days", 3);
                    group.propertyMin = 8;
                    group.propertyMax = 31;
                    toInsert.add(new ImmutableTriple<>(group, null, Collections.emptyList()));
                    group = new Group(Grouping.DL_DATE, "Last 60 days", 4);
                    group.propertyMin = 31;
                    group.propertyMax = 61;
                    toInsert.add(new ImmutableTriple<>(group, null, Collections.emptyList()));
                    group = new Group(Grouping.DL_DATE, "Last year", 5);
                    group.propertyMin = 61;
                    group.propertyMax = 366;
                    toInsert.add(new ImmutableTriple<>(group, null, Collections.emptyList()));
                    group = new Group(Grouping.DL_DATE, "A long time ago", 6);
                    group.propertyMin = 366;
                    group.propertyMax = 9999999;
                    toInsert.add(new ImmutableTriple<>(group, null, Collections.emptyList()));
                }
            }

            // Actual insert is inside its dedicated loop to allow displaying a proper progress bar
            Timber.i("Create non-existing groupings : %s relations to create", bookInsertCount);
            float pos = 1;
            for (ImmutableTriple<Group, Attribute, List<Content>> data : toInsert) {
                db.insertGroup(data.left);
                if (data.middle != null) data.middle.putGroup(data.left);
                int order = 0;
                for (Content book : data.right) {
                    GroupItem item = new GroupItem(book, data.left, order++);
                    db.insertGroupItem(item);
                    emitter.onNext(pos++ / bookInsertCount);
                }
            }
            Timber.i("Create non-existing groupings : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }

    private static void computeReadingProgress(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Compute missing downloaded Content size according to underlying ImageFile sizes
            Timber.i("Computing downloaded content read progress : start");
            List<Content> contents = db.selectDownloadedContentWithNoReadProgress();
            Timber.i("Computing downloaded content read progress : %s books detected", contents.size());
            int max = contents.size();
            float pos = 1;
            for (Content c : contents) {
                c.computeReadProgress();
                db.insertContent(c);
                emitter.onNext(pos++ / max);
            }
            Timber.i("Computing downloaded content read progress : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }

    private static void cleanOrphanAttributes(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Compute missing downloaded Content size according to underlying ImageFile sizes
            Timber.i("Cleaning orphan attributes : start");
            db.cleanupOrphanAttributes();
            Timber.i("Cleaning orphan attributes : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }
}
