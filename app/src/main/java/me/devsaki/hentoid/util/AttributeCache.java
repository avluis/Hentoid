package me.devsaki.hentoid.util;

import android.net.Uri;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.database.domains.Attribute;
import timber.log.Timber;

/**
 * Manage in-memory attribute cache with expiry date
 *
 * TODO - manage on-disk cache
 */
public class AttributeCache {

    private final static int EXPIRY_FILE_VERSION = 1;
    private final static int COLLECTION_FILE_VERSION = 1;

    private static File cacheDir;
    private static Map<String, Date> collectionExpiry;
    private static Map<String, List<Attribute>> collection;

    public static List<Attribute> getFromCache(String key)
    {
        // TODO read cache from disk
        if (null == collectionExpiry) return null;

        if (collection.containsKey(key) && collectionExpiry.get(key).after(new Date())) return collection.get(key);
        else return null;
    }

    public static void setCache(String key, List<Attribute> value, Date expiryDateUTC) {
        // TODO common init with getFromCache where disk-cached data is fetched
        if (null == collectionExpiry) {
            collectionExpiry = new HashMap<>();
            collection = new HashMap<>();
            cacheDir = HentoidApp.getAppContext().getExternalCacheDir();
        }

        // Convert UTC to local timezone
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date expiryDateLocal = new Date(simpleDateFormat.format(expiryDateUTC));

        collectionExpiry.put(key, expiryDateLocal);
        collection.put(key, new ArrayList<>(value));

        // Clean up cache directory
        Timber.d("Cache directory cleaned successfully: %s", FileHelper.cleanDirectory(cacheDir));

        // Put expiry dates in cache
        Uri destinationUri = Uri.parse(cacheDir + "/expiries.cache");
        File file = new File(String.valueOf(destinationUri));
        try {
            try (DataOutputStream output = new DataOutputStream(FileHelper.getOutputStream(file))) {
                saveExpiriesToStream(output);
                output.flush();
            }
        } catch (IOException e) {
            Timber.w(e, "Error when creating master data expiry cache");
        }

        // Put master data in cache
        destinationUri = Uri.parse(cacheDir + "/" + key + ".cache");
        file = new File(String.valueOf(destinationUri));
        try {
            try (DataOutputStream output = new DataOutputStream(FileHelper.getOutputStream(file))) {
                saveCacheToStream(key, output);
                output.flush();
            }
        } catch (IOException e) {
            Timber.w(e, "Error when creating master data cache for key %s", key);
        }
    }

    private static void saveExpiriesToStream(DataOutputStream output) throws IOException
    {
        output.write(EXPIRY_FILE_VERSION);
        output.write(collectionExpiry.size());

        for (String key : collectionExpiry.keySet())
        {
            output.writeUTF(key);
            output.writeLong(collectionExpiry.get(key).getTime());
        }
    }

    private static void loadExpiriesFromStream(DataInputStream input) throws IOException
    {
        input.readInt(); // File version
        int collectionSize = input.readInt();

        collectionExpiry.clear();

        String key;
        Date value;
        for (int i=0; i<collectionSize; i++)
        {
            key = input.readUTF();
            value = new Date();
            value.setTime(input.readLong());
            collectionExpiry.put(key, value);
        }
    }

    private static void saveCacheToStream(String key, DataOutputStream output) throws IOException
    {
        List<Attribute> cacheCollection = collection.get(key);

        output.write(COLLECTION_FILE_VERSION);
        output.write(collection.size());

        for (Attribute a : cacheCollection)
        {
            a.saveToStream(output);
        }
    }

    private static void loadCacheFromStream(String key, DataInputStream input) throws IOException
    {
        List<Attribute> attrs = new ArrayList<>();

        input.readInt(); // File version
        int collectionSize = input.readInt();

        for (int i=0; i<collectionSize; i++)
        {
            Attribute a = new Attribute();
            attrs.add(a.loadFromStream(input));
        }

        collection.put(key, attrs);
    }
}
