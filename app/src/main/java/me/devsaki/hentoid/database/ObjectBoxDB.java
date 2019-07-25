package me.devsaki.hentoid.database;

import android.content.Context;
import android.util.SparseIntArray;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.android.AndroidObjectBrowser;
import io.objectbox.query.LazyList;
import io.objectbox.query.Query;
import io.objectbox.query.QueryBuilder;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeLocation;
import me.devsaki.hentoid.database.domains.Attribute_;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Content_;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ErrorRecord_;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.ImageFile_;
import me.devsaki.hentoid.database.domains.MyObjectBox;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.QueueRecord_;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import timber.log.Timber;

import static com.annimon.stream.Collectors.toList;

public class ObjectBoxDB {

    // TODO - put indexes

    private static final int[] visibleContentStatus = new int[]{StatusContent.DOWNLOADED.getCode(),
            StatusContent.ERROR.getCode(),
            StatusContent.MIGRATED.getCode()};

    private static final List<Integer> visibleContentStatusAsList = Helper.getListFromPrimitiveArray(visibleContentStatus);

    private static ObjectBoxDB instance;

    private final BoxStore store;


    private ObjectBoxDB(Context context) {
        store = MyObjectBox.builder().androidContext(context.getApplicationContext()).build();

        if (BuildConfig.DEBUG && BuildConfig.INCLUDE_OBJECTBOX_BROWSER) {
            boolean started = new AndroidObjectBrowser(store).start(context.getApplicationContext());
            Timber.i("ObjectBrowser started: %s", started);
        }
    }


    // Use this to get db instance
    public static synchronized ObjectBoxDB getInstance(Context context) {
        // Use application context only
        if (instance == null) {
            instance = new ObjectBoxDB(context);
        }

        return instance;
    }

    public long insertContent(Content content) {
        Box<Attribute> attrBox = store.boxFor(Attribute.class);
        Query attrByUniqueKey = attrBox.query().equal(Attribute_.type, 0).equal(Attribute_.name, "").build();
        List<Attribute> attributes = content.getAttributes();

        // Master data management managed manually
        // Ensure all known attributes are replaced by their ID before being inserted
        // Watch https://github.com/objectbox/objectbox-java/issues/509 for a lighter solution based on @Unique annotation
        Attribute dbAttr;
        Attribute inputAttr;
        for (int i = 0; i < attributes.size(); i++) {
            inputAttr = attributes.get(i);
            dbAttr = (Attribute) attrByUniqueKey.setParameter(Attribute_.name, inputAttr.getName())
                    .setParameter(Attribute_.type, inputAttr.getType().getCode())
                    .findFirst();
            if (dbAttr != null) {
                attributes.set(i, dbAttr); // If existing -> set the existing attribute
                dbAttr.addLocationsFrom(inputAttr);
                attrBox.put(dbAttr);
            } else {
                inputAttr.setName(inputAttr.getName().toLowerCase().trim()); // If new -> normalize the attribute
            }
        }

        return store.boxFor(Content.class).put(content);
    }

    long countContentEntries() {
        return store.boxFor(Content.class).count();
    }

    public void updateContentStatus(StatusContent updateFrom, StatusContent updateTo) {
        List<Content> content = selectContentByStatus(updateFrom);
        for (int i = 0; i < content.size(); i++) content.get(i).setStatus(updateTo);

        store.boxFor(Content.class).put(content);
    }

    List<Content> selectContentByStatus(StatusContent status) {
        return selectContentByStatusCodes(new int[]{status.getCode()});
    }

    private List<Content> selectContentByStatusCodes(int[] statusCodes) {
        return store.boxFor(Content.class).query().in(Content_.status, statusCodes).build().find();
    }

    /*
    Remove all books in the library but keep the download queue intact
     */
    public void deleteAllBooks() {
        // All statuses except DOWNLOADING and PAUSED that imply the book is in the download queue
        int[] storedContentStatus = new int[]{
                StatusContent.SAVED.getCode(),
                StatusContent.DOWNLOADED.getCode(),
                StatusContent.ERROR.getCode(),
                StatusContent.MIGRATED.getCode(),
                StatusContent.IGNORED.getCode(),
                StatusContent.UNHANDLED_ERROR.getCode(),
                StatusContent.CANCELED.getCode(),
                StatusContent.ONLINE.getCode()
        };

        // Base content that has to be removed
        long[] deletableContentId = store.boxFor(Content.class).query().in(Content_.status, storedContentStatus).build().findIds();
        deleteContentById(deletableContentId);
    }

    public void deleteContent(Content content) {
        deleteContentById(content.getId());
    }

    private void deleteContentById(long contentId) {
        deleteContentById(new long[]{contentId});
    }

    /**
     * Remove the given content and all related objects from the DB
     * NB : ObjectBox v2.3.1 does not support cascade delete, so everything has to be done manually
     *
     * @param contentId IDs of the contents to be removed from the DB
     */
    private void deleteContentById(long[] contentId) {
        Box<ErrorRecord> errorBox = store.boxFor(ErrorRecord.class);
        Box<ImageFile> imageFileBox = store.boxFor(ImageFile.class);
        Box<Attribute> attributeBox = store.boxFor(Attribute.class);
        Box<AttributeLocation> locationBox = store.boxFor(AttributeLocation.class);
        Box<Content> contentBox = store.boxFor(Content.class);

        for (long id : contentId) {
            Content c = contentBox.get(id);
            if (c != null)
                store.runInTx(() -> {
                    if (c.getImageFiles() != null) {
                        for (ImageFile i : c.getImageFiles())
                            imageFileBox.remove(i);   // Delete imageFiles
                        c.getImageFiles().clear();                                      // Clear links to all imageFiles
                    }

                    if (c.getErrorLog() != null) {
                        for (ErrorRecord e : c.getErrorLog())
                            errorBox.remove(e);   // Delete error records
                        c.getErrorLog().clear();                                    // Clear links to all errorRecords
                    }

                    // Delete attribute when current content is the only content left on the attribute
                    for (Attribute a : c.getAttributes())
                        if (1 == a.contents.size()) {
                            for (AttributeLocation l : a.getLocations())
                                locationBox.remove(l); // Delete all locations
                            a.getLocations().clear();                                           // Clear location links
                            attributeBox.remove(a);                                             // Delete the attribute itself
                        }
                    c.getAttributes().clear();                                      // Clear links to all attributes

                    contentBox.remove(c);                                           // Remove the content itself
                });
        }
    }

    public void updateContentReads(Content content) {
        Box<Content> contentBox = store.boxFor(Content.class);
        Content c = contentBox.get(content.getId());
        if (c != null) {
            c.setReads(content.getReads());
            c.setLastReadDate(content.getLastReadDate());
            contentBox.put(c);
        }
    }

    public List<QueueRecord> selectQueue() {
        return store.boxFor(QueueRecord.class).query().order(QueueRecord_.rank).build().find();
    }

    public List<Content> selectQueueContents() {
        List<Content> result = new ArrayList<>();
        List<QueueRecord> queueRecords = selectQueue();
        for (QueueRecord q : queueRecords) result.add(q.content.getTarget());
        return result;
    }

    long selectMaxQueueOrder() {
        return store.boxFor(QueueRecord.class).query().build().property(QueueRecord_.rank).max();
    }

    public void insertQueue(long id, int order) {
        store.boxFor(QueueRecord.class).put(new QueueRecord(id, order));
    }

    public void udpateQueue(long contentId, int newOrder) {
        Box<QueueRecord> queueRecordBox = store.boxFor(QueueRecord.class);

        QueueRecord record = queueRecordBox.query().equal(QueueRecord_.contentId, contentId).order(QueueRecord_.rank).build().findFirst();
        if (record != null) {
            record.rank = newOrder;
            queueRecordBox.put(record);
        }
    }

    public void deleteQueue(Content content) {
        deleteQueue(content.getId());
    }

    public void deleteQueue(int queueIndex) {
        store.boxFor(QueueRecord.class).remove(selectQueue().get(queueIndex).id);
    }

    private void deleteQueue(long contentId) {
        Box<QueueRecord> queueRecordBox = store.boxFor(QueueRecord.class);
        QueueRecord record = queueRecordBox.query().equal(QueueRecord_.contentId, contentId).build().findFirst();

        if (record != null) {
            queueRecordBox.remove(record);
        }
    }

    public void deleteAllQueue() {
        store.boxFor(QueueRecord.class).removeAll();
    }

    long countAllContent() {
        return countContentSearch("", Collections.emptyList(), false);
    }

    @Nullable
    public Content selectContentById(long id) {
        return store.boxFor(Content.class).get(id);
    }

    @Nullable
    public Content selectContentByUrl(String url) {
        return store.boxFor(Content.class).query().equal(Content_.url, url).build().findFirst();
    }

    @Nullable
    public Attribute selectAttributeById(long id) {
        return store.boxFor(Attribute.class).get(id);
    }

    private static long[] getIdsFromAttributes(@Nonnull List<Attribute> attrs) {
        long[] result = new long[attrs.size()];
        if (!attrs.isEmpty()) {
            int index = 0;
            for (Attribute a : attrs) result[index++] = a.getId();
        }
        return result;
    }

    private void applyOrderStyle(QueryBuilder<Content> query, int orderStyle) {
        switch (orderStyle) {
            case Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_FIRST:
                query.orderDesc(Content_.downloadDate);
                break;
            case Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_LAST:
                query.order(Content_.downloadDate);
                break;
            case Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA:
                query.order(Content_.title);
                break;
            case Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA_INVERTED:
                query.orderDesc(Content_.title);
                break;
            case Preferences.Constant.ORDER_CONTENT_LEAST_READ:
                query.order(Content_.reads).order(Content_.lastReadDate);
                break;
            case Preferences.Constant.ORDER_CONTENT_MOST_READ:
                query.orderDesc(Content_.reads).orderDesc(Content_.lastReadDate);
                break;
            case Preferences.Constant.ORDER_CONTENT_LAST_READ:
                query.orderDesc(Content_.lastReadDate);
                break;
            case Preferences.Constant.ORDER_CONTENT_RANDOM:
                // That one's tricky - see https://github.com/objectbox/objectbox-java/issues/17 => Implemented post-query build
                break;
            default:
                // Nothing
        }
    }

    private Query<Content> queryContentSearchContent(String title, List<Attribute> metadata, boolean filterFavourites, int orderStyle) {
        AttributeMap metadataMap = new AttributeMap();
        metadataMap.addAll(metadata);

        boolean hasTitleFilter = (title != null && title.length() > 0);
        boolean hasSiteFilter = metadataMap.containsKey(AttributeType.SOURCE)
                                && (metadataMap.get(AttributeType.SOURCE) != null)
                                && !(metadataMap.get(AttributeType.SOURCE).isEmpty());
        boolean hasTagFilter = metadataMap.keySet().size() > (hasSiteFilter ? 1 : 0);

        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (hasSiteFilter)
            query.in(Content_.site, getIdsFromAttributes(metadataMap.get(AttributeType.SOURCE)));
        if (filterFavourites) query.equal(Content_.favourite, true);
        if (hasTitleFilter) query.contains(Content_.title, title);
        if (hasTagFilter) {
            for (AttributeType attrType : metadataMap.keySet()) {
                if (!attrType.equals(AttributeType.SOURCE)) { // Not a "real" attribute in database
                    List<Attribute> attrs = metadataMap.get(attrType);
                    if (attrs != null && !attrs.isEmpty()) {
                        query.in(Content_.id, getFilteredContent(attrs, false));
                    }
                }
            }
        }
        applyOrderStyle(query, orderStyle);

        return query.build();
    }

    private Query<Content> queryContentUniversalContent(String queryStr, boolean filterFavourites, long[] additionalIds, int orderStyle) {
        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (filterFavourites) query.equal(Content_.favourite, true);
        query.contains(Content_.title, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE);
        query.or().equal(Content_.uniqueSiteId, queryStr);
//        query.or().link(Content_.attributes).contains(Attribute_.name, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE); // Use of or() here is not possible yet with ObjectBox v2.3.1
        query.or().in(Content_.id, additionalIds);
        applyOrderStyle(query, orderStyle);

        return query.build();
    }

    private Query<Content> queryContentUniversalAttributes(String queryStr, boolean filterFavourites) {
        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (filterFavourites) query.equal(Content_.favourite, true);
        query.link(Content_.attributes).contains(Attribute_.name, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE);

        return query.build();
    }

    long countContentSearch(String title, List<Attribute> tags, boolean filterFavourites) {
        Query<Content> query = queryContentSearchContent(title, tags, filterFavourites, Preferences.Constant.ORDER_CONTENT_NONE);
        return query.count();
    }

    private static List<Content> shuffleRandomSort(Query<Content> query, int start, int booksPerPage) {
        LazyList<Content> lazyList = query.findLazy();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < lazyList.size(); i++) order.add(i);
        Collections.shuffle(order, new Random(RandomSeedSingleton.getInstance().getSeed()));

        int maxPage;
        if (booksPerPage < 0) {
            start = 0;
            maxPage = order.size();
        } else maxPage = Math.min(start + booksPerPage, order.size());

        List<Content> result = new ArrayList<>();
        for (int i = start; i < maxPage; i++) {
            result.add(lazyList.get(order.get(i)));
        }
        return result;
    }

    private static long[] shuffleRandomSortId(Query<Content> query, int start, int booksPerPage) {
        LazyList<Content> lazyList = query.findLazy();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < lazyList.size(); i++) order.add(i);
        Collections.shuffle(order, new Random(RandomSeedSingleton.getInstance().getSeed()));

        int maxPage;
        if (booksPerPage < 0) {
            start = 0;
            maxPage = order.size();
        } else maxPage = Math.min(start + booksPerPage, order.size());

        List<Long> result = new ArrayList<>();
        for (int i = start; i < maxPage; i++) {
            result.add(lazyList.get(order.get(i)).getId());
        }
        return Helper.getPrimitiveLongArrayFromList(result);
    }

    List<Content> selectContentSearch(String title, int page, int booksPerPage, List<Attribute> tags, boolean filterFavourites, int orderStyle) {
        List<Content> result;
        int start = (page - 1) * booksPerPage;
        Query<Content> query = queryContentSearchContent(title, tags, filterFavourites, orderStyle);

        if (orderStyle != Preferences.Constant.ORDER_CONTENT_RANDOM) {
            if (booksPerPage < 0) result = query.find();
            else result = query.find(start, booksPerPage);
        } else {
            result = shuffleRandomSort(query, start, booksPerPage);
        }
        return setQueryIndexes(result, page, booksPerPage);
    }

    long[] selectContentSearchId(String title, int page, int booksPerPage, List<Attribute> tags, boolean filterFavourites, int orderStyle) {
        long[] result;
        int start = (page - 1) * booksPerPage;
        Query<Content> query = queryContentSearchContent(title, tags, filterFavourites, orderStyle);

        if (orderStyle != Preferences.Constant.ORDER_CONTENT_RANDOM) {
            if (booksPerPage < 0) result = query.findIds();
            else result = query.findIds(start, booksPerPage);
        } else {
            result = shuffleRandomSortId(query, start, booksPerPage);
        }
        return result;
    }

    List<Content> selectContentUniversal(String queryStr, int page, int booksPerPage, boolean filterFavourites, int orderStyle) {
        List<Content> result;
        int start = (page - 1) * booksPerPage;
        // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497 and https://github.com/objectbox/objectbox-java/issues/533)
        // querying Content and attributes have to be done separately
        Query<Content> contentAttrSubQuery = queryContentUniversalAttributes(queryStr, filterFavourites);
        Query<Content> query = queryContentUniversalContent(queryStr, filterFavourites, contentAttrSubQuery.findIds(), orderStyle);

        if (orderStyle != Preferences.Constant.ORDER_CONTENT_RANDOM) {
            if (booksPerPage < 0) result = query.find();
            else result = query.find(start, booksPerPage);
        } else {
            result = shuffleRandomSort(query, start, booksPerPage);
        }
        return setQueryIndexes(result, page, booksPerPage);
    }

    long[] selectContentUniversalId(String queryStr, int page, int booksPerPage, boolean filterFavourites, int orderStyle) {
        long[] result;
        int start = (page - 1) * booksPerPage;
        // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497 and https://github.com/objectbox/objectbox-java/issues/533)
        // querying Content and attributes have to be done separately
        Query<Content> contentAttrSubQuery = queryContentUniversalAttributes(queryStr, filterFavourites);
        Query<Content> query = queryContentUniversalContent(queryStr, filterFavourites, contentAttrSubQuery.findIds(), orderStyle);

        if (orderStyle != Preferences.Constant.ORDER_CONTENT_RANDOM) {
            if (booksPerPage < 0) result = query.findIds();
            else result = query.findIds(start, booksPerPage);
        } else {
            result = shuffleRandomSortId(query, start, booksPerPage);
        }
        return result;
    }

    private List<Content> setQueryIndexes(List<Content> content, int page, int booksPerPage) {
        for (int i = 0; i < content.size(); i++)
            content.get(i).setQueryOrder((page - 1) * booksPerPage + i);
        return content;
    }

    long countContentUniversal(String queryStr, boolean filterFavourites) {
        // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497 and https://github.com/objectbox/objectbox-java/issues/533)
        // querying Content and attributes have to be done separately
        Query<Content> contentAttrSubQuery = queryContentUniversalAttributes(queryStr, filterFavourites);
        Query<Content> query = queryContentUniversalContent(queryStr, filterFavourites, contentAttrSubQuery.findIds(), Preferences.Constant.ORDER_CONTENT_NONE);
        return query.count();
    }

    private long[] getFilteredContent(List<Attribute> attrs, boolean filterFavourites) {
        if (null == attrs || attrs.isEmpty()) return new long[0];

        // Pre-build queries to reuse them efficiently within the loops
        QueryBuilder<Content> contentFromSourceQueryBuilder = store.boxFor(Content.class).query();
        contentFromSourceQueryBuilder.in(Content_.status, visibleContentStatus);
        contentFromSourceQueryBuilder.equal(Content_.site, 1);
        if (filterFavourites) contentFromSourceQueryBuilder.equal(Content_.favourite, true);
        Query<Content> contentFromSourceQuery = contentFromSourceQueryBuilder.build();

        QueryBuilder<Content> contentFromAttributesQueryBuilder = store.boxFor(Content.class).query();
        contentFromAttributesQueryBuilder.in(Content_.status, visibleContentStatus);
        if (filterFavourites) contentFromSourceQueryBuilder.equal(Content_.favourite, true);
        contentFromAttributesQueryBuilder.link(Content_.attributes)
                .equal(Attribute_.type, 0)
                .equal(Attribute_.name, "");
        Query<Content> contentFromAttributesQuery = contentFromAttributesQueryBuilder.build();

        // Cumulative query loop
        // Each iteration restricts the results of the next because advanced search uses an AND logic
        List<Long> results = Collections.emptyList();
        long[] ids;

        for (Attribute attr : attrs) {
            if (attr.getType().equals(AttributeType.SOURCE)) {
                ids = contentFromSourceQuery.setParameter(Content_.site, attr.getId()).findIds();
            } else {
                ids = contentFromAttributesQuery.setParameter(Attribute_.type, attr.getType().getCode())
                        .setParameter(Attribute_.name, attr.getName()).findIds();
            }
            if (results.isEmpty()) results = Helper.getListFromPrimitiveArray(ids);
            else {
                // Filter results with newly found IDs (only common IDs should stay)
                List<Long> idsAsList = Helper.getListFromPrimitiveArray(ids);
                results.retainAll(idsAsList);
            }
        }

        return Helper.getPrimitiveLongArrayFromList(results);
    }

    List<Attribute> selectAvailableSources() {
        return selectAvailableSources(null);
    }

    List<Attribute> selectAvailableSources(List<Attribute> filter) {
        List<Attribute> result = new ArrayList<>();

        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (filter != null && !filter.isEmpty()) {
            AttributeMap metadataMap = new AttributeMap();
            metadataMap.addAll(filter);

            List<Attribute> params = metadataMap.get(AttributeType.SOURCE);
            if (params != null && !params.isEmpty())
                query.in(Content_.site, getIdsFromAttributes(params));

            for (AttributeType attrType : metadataMap.keySet()) {
                if (!attrType.equals(AttributeType.SOURCE)) { // Not a "real" attribute in database
                    List<Attribute> attrs = metadataMap.get(attrType);
                    if (attrs != null && !attrs.isEmpty()) {
                        query.in(Content_.id, getFilteredContent(attrs, false));
                    }
                }
            }
        }

        List<Content> content = query.build().find();

        // SELECT field, COUNT(*) GROUP BY (field) is not implemented in ObjectBox v2.3.1
        // => Group by and count have to be done manually (thanks God Stream exists !)
        // Group and count by source
        Map<Site, List<Content>> map = Stream.of(content).collect(Collectors.groupingBy(Content::getSite));
        for (Site s : map.keySet()) {
            result.add(new Attribute(AttributeType.SOURCE, s.getDescription()).setExternalId(s.getCode()).setCount(map.get(s).size()));
        }
        // Order by count desc
        result = Stream.of(result).sortBy(a -> -a.getCount()).collect(toList());

        return result;
    }

    private Query<Attribute> queryAvailableAttributes(AttributeType type, String filter, List<Long> filteredContent) {
        QueryBuilder<Attribute> query = store.boxFor(Attribute.class).query();
        if (!filteredContent.isEmpty())
            query.filter(attr -> (Stream.of(attr.contents).filter(c -> filteredContent.contains(c.getId())).filter(c -> visibleContentStatusAsList.contains(c.getStatus().getCode())).count() > 0));
//            query.link(Attribute_.contents).in(Content_.id, filteredContent).in(Content_.status, visibleContentStatus); <-- does not work for an obscure reason; need to reproduce that on a clean project and submit it to ObjectBox
        query.equal(Attribute_.type, type.getCode());
        if (filter != null && !filter.trim().isEmpty())
            query.contains(Attribute_.name, filter.trim(), QueryBuilder.StringOrder.CASE_INSENSITIVE);

        return query.build();
    }

    long countAvailableAttributes(AttributeType type, List<Attribute> attributeFilter, String filter, boolean filterFavourites) {
        List<Long> filteredContent = Helper.getListFromPrimitiveArray(getFilteredContent(attributeFilter, filterFavourites));
        return queryAvailableAttributes(type, filter, filteredContent).count();
    }

    @SuppressWarnings("squid:S2184")
        // In our case, limit() argument has to be human-readable -> no issue concerning its type staying in the int range
    List<Attribute> selectAvailableAttributes(AttributeType type, List<Attribute> attributeFilter, String filter, boolean filterFavourites, int sortOrder, int page, int itemsPerPage) {
        long[] filteredContent = getFilteredContent(attributeFilter, filterFavourites);
        List<Long> filteredContentAsList = Helper.getListFromPrimitiveArray(filteredContent);
        List<Attribute> result = queryAvailableAttributes(type, filter, filteredContentAsList).find();

        // Compute attribute count for sorting
        int count;
        for (Attribute a : result) {
            if (0 == filteredContent.length) count = a.contents.size();
            else {
                count = 0;
                for (Content c : a.contents) if (filteredContentAsList.contains(c.getId())) count++;
            }
            a.setCount(count);
        }

        // Apply sort order
        Stream<Attribute> s = Stream.of(result);
        if (Preferences.Constant.ORDER_ATTRIBUTES_ALPHABETIC == sortOrder) {
            s = s.sortBy(a -> -a.getCount()).sortBy(Attribute::getName);
        } else {
            s = s.sortBy(Attribute::getName).sortBy(a -> -a.getCount());
        }

        // Apply paging
        if (itemsPerPage > 0) {
            int start = (page - 1) * itemsPerPage;
            s = s.limit(page * itemsPerPage).skip(start); // squid:S2184 here because int * int -> int (not long)
        }
        return s.collect(toList());
    }

    SparseIntArray countAvailableAttributesPerType() {
        return countAvailableAttributesPerType(null);
    }

    SparseIntArray countAvailableAttributesPerType(List<Attribute> attributeFilter) {
        // Get Content filtered by current selection
        long[] filteredContent = getFilteredContent(attributeFilter, false);
        // Get available attributes of the resulting content list
        QueryBuilder<Attribute> query = store.boxFor(Attribute.class).query();
        if (filteredContent.length > 0)
            query.link(Attribute_.contents).in(Content_.id, filteredContent).in(Content_.status, visibleContentStatus);

        List<Attribute> attributes = query.build().find();

        SparseIntArray result = new SparseIntArray();
        // SELECT field, COUNT(*) GROUP BY (field) is not implemented in ObjectBox v2.3.1
        // => Group by and count have to be done manually (thanks God Stream exists !)
        // Group and count by type
        Map<AttributeType, List<Attribute>> map = Stream.of(attributes).collect(Collectors.groupingBy(Attribute::getType));
        for (AttributeType t : map.keySet()) {
            result.append(t.getCode(), map.get(t).size());
        }

        return result;
    }

    public void updateImageFileStatusAndParams(ImageFile image) {
        Box<ImageFile> imgBox = store.boxFor(ImageFile.class);
        ImageFile img = imgBox.get(image.getId());
        if (img != null) {
            img.setStatus(image.getStatus());
            img.setDownloadParams(image.getDownloadParams());
            imgBox.put(img);
        }
    }

    void updateImageFileUrl(ImageFile image) {
        Box<ImageFile> imgBox = store.boxFor(ImageFile.class);
        ImageFile img = imgBox.get(image.getId());
        if (img != null) {
            img.setUrl(image.getUrl());
            imgBox.put(img);
        }
    }

    public SparseIntArray countProcessedImagesById(long contentId) {
        QueryBuilder<ImageFile> imgQuery = store.boxFor(ImageFile.class).query();
        imgQuery.equal(ImageFile_.contentId, contentId);
        List<ImageFile> images = imgQuery.build().find();

        SparseIntArray result = new SparseIntArray();
        // SELECT field, COUNT(*) GROUP BY (field) is not implemented in ObjectBox v2.3.1
        // => Group by and count have to be done manually (thanks God Stream exists !)
        // Group and count by type
        Map<StatusContent, List<ImageFile>> map = Stream.of(images).collect(Collectors.groupingBy(ImageFile::getStatus));
        for (StatusContent t : map.keySet()) {
            result.append(t.getCode(), map.get(t).size());
        }

        return result;
    }

    public List<Content> selectContentBySourceId(Site site, List<String> uniqueIds) {
        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, new int[]{StatusContent.DOWNLOADED.getCode(),
                StatusContent.ERROR.getCode(),
                StatusContent.MIGRATED.getCode(),
                StatusContent.DOWNLOADING.getCode(),
                StatusContent.PAUSED.getCode()});
        query.equal(Content_.site, site.getCode());
        query.in(Content_.uniqueSiteId, uniqueIds.toArray(new String[0]));

        return query.build().find();
    }

    List<Content> selectContentWithOldPururinHost() {
        return store.boxFor(Content.class).query().contains(Content_.coverImageUrl, "://api.pururin.io/images/").build().find();
    }

    public void insertErrorRecord(ErrorRecord record) {
        store.boxFor(ErrorRecord.class).put(record);
    }

    public List<ErrorRecord> selectErrorRecordByContentId(long contentId) {
        return store.boxFor(ErrorRecord.class).query().equal(ErrorRecord_.contentId, contentId).build().find();
    }

    public void deleteErrorRecords(long contentId) {
        List<ErrorRecord> records = selectErrorRecordByContentId(contentId);
        store.boxFor(ErrorRecord.class).remove(records);
    }

    public void insertImageFile(ImageFile img) {
        if (img.getId() > 0) store.boxFor(ImageFile.class).put(img);
    }

    @Nullable
    public ImageFile selectImageFile(long id) {
        if (id > 0) return store.boxFor(ImageFile.class).get(id);
        else return null;
    }
}
