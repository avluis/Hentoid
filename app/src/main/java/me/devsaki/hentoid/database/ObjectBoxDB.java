package me.devsaki.hentoid.database;

import android.content.Context;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.Query;
import io.objectbox.query.QueryBuilder;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Attribute_;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Content_;
import me.devsaki.hentoid.database.domains.MyObjectBox;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.QueueRecord_;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Preferences;

public class ObjectBoxDB {

    private static ObjectBoxDB instance;

    private final BoxStore store;
    private final int[] visibleContentStatus = new int[]{StatusContent.DOWNLOADED.getCode(),
            StatusContent.ERROR.getCode(),
            StatusContent.MIGRATED.getCode()};

    private ObjectBoxDB(Context context) {
        store = MyObjectBox.builder().androidContext(context).build();
    }


    // Use this to get db instance
    public static synchronized ObjectBoxDB getInstance(Context context) {
        // Use application context only
        if (instance == null) {
            instance = new ObjectBoxDB(context);
        }

        return instance;
    }

    public void insertContent(Content row) {
        store.boxFor(Content.class).put(row);
    }

    public long countContentEntries() {
        return store.boxFor(Content.class).count();
    }

    public void updateContentStatus(Content row) {
        Box<Content> contentBox = store.boxFor(Content.class);
        Content c = contentBox.get(row.getId());
        c.setStatus(row.getStatus());
        contentBox.put(c);
    }

    public void updateContentStatus(StatusContent updateFrom, StatusContent updateTo) {
        List<Content> content = selectContentByStatus(updateFrom);
        for (int i = 0; i < content.size(); i++) content.get(i).setStatus(updateTo);

        store.boxFor(Content.class).put(content);
    }

    public void updateContentFavourite(Content content) {
        Box<Content> contentBox = store.boxFor(Content.class);
        Content c = contentBox.get(content.getId());
        c.setFavourite(!c.isFavourite());
        contentBox.put(c);
    }

    public List<Content> selectContentByStatus(StatusContent status) {
        return store.boxFor(Content.class).query().equal(Content_.status, status.getCode()).build().find();
    }

    public void deleteContent(Content content) {
        store.boxFor(Content.class).remove(content);
    }

    public void updateContentReads(Content content) {
        Box<Content> contentBox = store.boxFor(Content.class);

        Content c = contentBox.get(content.getId());
        c.setReads(content.getReads());
        c.setLastReadDate(content.getLastReadDate());
        contentBox.put(c);
    }

    public List<QueueRecord> selectQueue() {
        return store.boxFor(QueueRecord.class).query().order(QueueRecord_.rank).build().find();
    }

    public List<Content> selectQueueContents() {
        List<Content> result = Collections.emptyList();
        List<QueueRecord> queueRecords = selectQueue();
        if (queueRecords.size() > 0) {
            long[] contentIds = new long[queueRecords.size()];
            int index = 0;
            for (QueueRecord q : queueRecords) contentIds[index++] = q.content.getTargetId();
            result = selectContentByIds(contentIds);
        }
        return result;
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

    public void deleteQueueById(long contentId) {
        store.boxFor(Content.class).remove(contentId);
    }

    long countAllContent() {
        return countContentByQuery("", Collections.emptyList(), false);
    }

    public Content selectContentById(long id) {
        return store.boxFor(Content.class).get(id);
    }

    private List<Content> selectContentByIds(long[] ids) {
        return store.boxFor(Content.class).query().in(Content_.id, ids).build().find();
    }

    public Attribute selectAttributeById(long id) {
        return store.boxFor(Attribute.class).get(id);
    }

    private long[] getIdsFromAttributes(@Nonnull List<Attribute> attrs) {
        long[] result = new long[attrs.size()];
        if (attrs.size() > 0) {
            int index = 0;
            for (Attribute a : attrs) result[index++] = a.getId();
        }
        return result;
    }

    private String[] getNamesFromAttributes(@Nonnull List<Attribute> attrs) {
        String[] result = new String[attrs.size()];
        if (attrs.size() > 0) {
            int index = 0;
            for (Attribute a : attrs) result[index++] = a.getName();
        }
        return result;
    }

    private void applyOrderStyle(QueryBuilder<Content> query, int orderStyle) {
        switch (orderStyle) {
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_FIRST:
                query.orderDesc(Content_.downloadDate);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_LAST:
                query.order(Content_.downloadDate);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_TITLE_ALPHA:
                query.order(Content_.title);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_TITLE_ALPHA_INVERTED:
                query.orderDesc(Content_.title);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_LEAST_READ:
                query.order(Content_.reads).order(Content_.lastReadDate);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_MOST_READ:
                query.orderDesc(Content_.reads).orderDesc(Content_.lastReadDate);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_READ:
                query.orderDesc(Content_.lastReadDate);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_RANDOM:
                // TODO - that one's tricky - see https://github.com/objectbox/objectbox-java/issues/17
//                sql += ContentTable.ORDER_RANDOM.replace("@6", String.valueOf(RandomSeedSingleton.getInstance().getRandomNumber()));
                break;
            default:
                // Nothing
        }
    }

    private Query<Content> buildContentSearchQuery(String title, List<Attribute> metadata, boolean filterFavourites, int orderStyle) {
        AttributeMap metadataMap = new AttributeMap();
        metadataMap.add(metadata);

        boolean hasTitleFilter = (title != null && title.length() > 0);
        boolean hasSiteFilter = metadataMap.containsKey(AttributeType.SOURCE) && (metadataMap.get(AttributeType.SOURCE) != null) && (metadataMap.get(AttributeType.SOURCE).size() > 0);
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
                    String[] attrNames = getNamesFromAttributes(metadataMap.get(attrType));
                    if (attrNames.length > 0)
                        query.link(Content_.attributes).equal(Attribute_.type, attrType.getCode()).in(Attribute_.name, attrNames, QueryBuilder.StringOrder.CASE_INSENSITIVE);
                }
            }
        }
        applyOrderStyle(query, orderStyle);

        return query.build();
    }

    // Target Function; does not work with ObjectBox v2.3.1
    private Query<Content> buildUniversalContentSearchQuery(String queryStr, boolean filterFavourites, int orderStyle) {
        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (filterFavourites) query.equal(Content_.favourite, true);
        query.contains(Content_.title, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE);
        query.or().link(Content_.attributes).contains(Attribute_.name, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE); // Use of or() here is not possible yet
        applyOrderStyle(query, orderStyle);

        return query.build();
    }

    // Workaround function for buildUniversalContentSearchQuery
    // Has to be combined with buildUniversalContentSearchQueryAttributes
    private Query<Content> buildUniversalContentSearchQueryContent(String queryStr, boolean filterFavourites, long[] additionalIds, int orderStyle) {
        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (filterFavourites) query.equal(Content_.favourite, true);
        query.contains(Content_.title, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE);
        query.or().in(Content_.id, additionalIds);
        applyOrderStyle(query, orderStyle);

        return query.build();
    }

    // Workaround function for buildUniversalContentSearchQuery
    // Has to be combined with buildUniversalContentSearchQueryContent
    private Query<Content> buildUniversalContentSearchQueryAttributes(String queryStr, boolean filterFavourites) {
        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (filterFavourites) query.equal(Content_.favourite, true);
        query.link(Content_.attributes).contains(Attribute_.name, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE);

        return query.build();
    }

    long countContentByQuery(String title, List<Attribute> tags, boolean filterFavourites) {
        Query<Content> query = buildContentSearchQuery(title, tags, filterFavourites, Preferences.Constant.PREF_ORDER_CONTENT_NONE);
        return query.count();
    }

    List<Content> selectContentByQuery(String title, int page, int booksPerPage, List<Attribute> tags, boolean filterFavourites, int orderStyle) {
        int start = (page - 1) * booksPerPage;
        Query<Content> query = buildContentSearchQuery(title, tags, filterFavourites, orderStyle);

        if (booksPerPage < 0) return query.find();
        else return query.find(start, booksPerPage);
    }

    List<Content> selectContentByUniqueQuery(String queryStr, int page, int booksPerPage, boolean filterFavourites, int orderStyle) {
        int start = (page - 1) * booksPerPage;
/*
        Query<Content> query = buildUniversalContentSearchQuery(queryStr, filterFavourites, orderStyle);
*/
        // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497 and https://github.com/objectbox/objectbox-java/issues/533)
        // querying Content and attributes will have to be done separately
        // TODO optimize by reusing query with parameters
        Query<Content> contentAttrSubQuery = buildUniversalContentSearchQueryAttributes(queryStr, filterFavourites);
        Query<Content> query = buildUniversalContentSearchQueryContent(queryStr, filterFavourites, contentAttrSubQuery.findIds(), orderStyle);

        if (booksPerPage < 0) return query.find();
        else return query.find(start, booksPerPage);
    }

    long countContentByUniqueQuery(String queryStr, boolean filterFavourites) {
/*
        Query<Content> query = buildUniversalContentSearchQuery(queryStr, filterFavourites, orderStyle);
*/
        // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497 and https://github.com/objectbox/objectbox-java/issues/533)
        // querying Content and attributes will have to be done separately
        // TODO optimize by reusing query with parameters
        Query<Content> contentAttrSubQuery = buildUniversalContentSearchQueryAttributes(queryStr, filterFavourites);
        Query<Content> query = buildUniversalContentSearchQueryContent(queryStr, filterFavourites, contentAttrSubQuery.findIds(), Preferences.Constant.PREF_ORDER_CONTENT_NONE);
        return query.count();
    }
}
