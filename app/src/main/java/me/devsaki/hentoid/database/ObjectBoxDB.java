package me.devsaki.hentoid.database;

import android.content.Context;

import io.objectbox.BoxStore;

public class ObjectBoxDB {

    private static ObjectBoxDB instance;

    private BoxStore store;


    private ObjectBoxDB(Context context) {
        //store = MyObjectBox.builder().androidContext(context).build();
    }


    // Use this to get db instance
    public static synchronized ObjectBoxDB getInstance(Context context) {
        // Use application context only
        if (instance == null) {
            instance = new ObjectBoxDB(context);
        }

        return instance;
    }

    public long countContentEntries() {
return 0;
    }
}
