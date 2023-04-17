package me.devsaki.hentoid.database;

import java.lang.reflect.Field;
import java.util.List;

import io.objectbox.internal.ReflectionCache;
import io.objectbox.query.Query;
import io.objectbox.query.QueryBuilder;
import io.objectbox.relation.ToMany;

public class DBHelper {

    static <T> List<T> safeFind(QueryBuilder<T> qb) {
        try (Query<T> q = qb.build()) {
            return q.find();
        }
    }

    static <T> List<T> safeFind(Query<T> q) {
        try {
            return q.find();
        } finally {
            q.close();
        }
    }

    static <T> T safeFindFirst(QueryBuilder<T> qb) {
        try (Query<T> q = qb.build()) {
            return q.findFirst();
        }
    }

    static <T> T safeFindFirst(Query<T> q) {
        try {
            return q.findFirst();
        } finally {
            q.close();
        }
    }

    static <T> long[] safeFindIds(QueryBuilder<T> qb) {
        try (Query<T> q = qb.build()) {
            return q.findIds();
        }
    }

    static <T> long[] safeFindIds(Query<T> q) {
        try {
            return q.findIds();
        } finally {
            q.close();
        }
    }

    static <T> long safeCount(QueryBuilder<T> qb) {
        try (Query<T> q = qb.build()) {
            return q.count();
        }
    }

    static <T> long safeCount(Query<T> q) {
        try {
            return q.count();
        } finally {
            q.close();
        }
    }

    static <T> void safeRemove(QueryBuilder<T> qb) {
        try (Query<T> q = qb.build()) {
            q.remove();
        }
    }

    static <T> void safeRemove(Query<T> q) {
        try {
            q.remove();
        } finally {
            q.close();
        }
    }

    // Inspired by ToMany.ensureBoxes
    public static boolean isDetached(Object entity) {
        Field boxStoreField = ReflectionCache.getInstance().getField(entity.getClass(), "__boxStore");
        try {
            return (null == boxStoreField.get(entity));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
