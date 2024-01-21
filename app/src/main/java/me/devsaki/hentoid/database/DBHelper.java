package me.devsaki.hentoid.database;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import io.objectbox.internal.ReflectionCache;
import io.objectbox.query.Query;
import io.objectbox.query.QueryBuilder;
import io.objectbox.relation.ToMany;
import io.objectbox.relation.ToOne;

@kotlin.SinceKotlin(version = "99999.0") // Java only; Kotlin has QueryX
public class DBHelper {

    static <T> List<T> safeFind(QueryBuilder<T> qb) {
        try (Query<T> q = qb.build()) {
            return q.find();
        }
    }

    static <T> List<T> safeFind(Query<T> q) {
        try (q) {
            return q.find();
        }
    }

    static <T> T safeFindFirst(QueryBuilder<T> qb) {
        try (Query<T> q = qb.build()) {
            return q.findFirst();
        }
    }

    static <T> T safeFindFirst(Query<T> q) {
        try (q) {
            return q.findFirst();
        }
    }

    static <T> long[] safeFindIds(QueryBuilder<T> qb) {
        try (Query<T> q = qb.build()) {
            return q.findIds();
        }
    }

    static <T> long[] safeFindIds(Query<T> q) {
        try (q) {
            return q.findIds();
        }
    }

    static <T> long safeCount(QueryBuilder<T> qb) {
        try (Query<T> q = qb.build()) {
            return q.count();
        }
    }

    static <T> long safeCount(Query<T> q) {
        try (q) {
            return q.count();
        }
    }

    static <T> void safeRemove(QueryBuilder<T> qb) {
        try (Query<T> q = qb.build()) {
            q.remove();
        }
    }

    static <T> void safeRemove(Query<T> q) {
        try (q) {
            q.remove();
        }
    }

    // Inspired by ToOne/ToMany.ensureBoxes
    public static <T> boolean isReachable(Object entity, ToOne<T> relationship) {
        if (null == relationship) return false;
        if (relationship.isResolved()) return true;
        Field boxStoreField = ReflectionCache.getInstance().getField(entity.getClass(), "__boxStore");
        try {
            return (null != boxStoreField.get(entity));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> boolean isReachable(Object entity, ToMany<T> relationship) {
        if (null == relationship) return false;
        if (relationship.isResolved()) return true;
        Field boxStoreField = ReflectionCache.getInstance().getField(entity.getClass(), "__boxStore");
        try {
            return (null != boxStoreField.get(entity));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> List<T> reach(Object entity, ToMany<T> relationship) {
        if (DBHelper.isReachable(entity, relationship)) return relationship;
        return Collections.emptyList();
    }

    @Nullable
    public static <T> T reach(Object entity, ToOne<T> relationship) {
        if (!relationship.isResolved()) {
            Field boxStoreField = ReflectionCache.getInstance().getField(entity.getClass(), "__boxStore");
            try {
                if (null == boxStoreField.get(entity)) return null;
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return relationship.getTarget();
    }
}
