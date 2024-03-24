package me.devsaki.hentoid.database;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import io.objectbox.internal.ReflectionCache;
import io.objectbox.relation.ToMany;
import io.objectbox.relation.ToOne;

@kotlin.SinceKotlin(version = "99999.0") // Java only; Kotlin has QueryX
public class DBHelper {

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
