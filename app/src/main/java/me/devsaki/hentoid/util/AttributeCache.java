package me.devsaki.hentoid.util;

import org.json.JSONObject;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import me.devsaki.hentoid.database.domains.Attribute;

/**
 * Manage in-memory attribute cache with expiry date
 *
 * TODO - manage on-disk cache
 */
public class AttributeCache {

    private static File cacheDir;
    private static Map<String, Date> collectionExpiry;
    private static Map<String, JSONObject> collection;

    public static JSONObject getFromCache(String key)
    {
        if (null == collectionExpiry) return null;

        if (collection.containsKey(key) && collectionExpiry.get(key).after(new Date())) return collection.get(key);
        else return null;
    }

    public static void setCache(String key, JSONObject value, Date expiryDateUTC)
    {
        if (null == collectionExpiry)
        {
            collectionExpiry = new HashMap<>();
            collection = new HashMap<>();
//            cacheDir = HentoidApp.getAppContext().getExternalCacheDir();
        }

        // Convert UTC to local timezone
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date expiryDateLocal = new Date(simpleDateFormat.format(expiryDateUTC));

//            expiryDateLocal = simpleDateFormat.parse(expiryDateUTC.toString());

        collectionExpiry.put(key, expiryDateLocal);
        collection.put(key, value);
    }
}
