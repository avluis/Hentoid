package me.devsaki.hentoid.components;

import android.app.ListFragment;
import android.content.SharedPreferences;

import me.devsaki.hentoid.database.HentoidDB;

/**
 * Created by neko on 06/06/2015.
 */

public abstract class HentoidFragment extends ListFragment {

    protected SharedPreferences getSharedPreferences() {
        return ((HentoidActivity) getActivity()).getSharedPreferences();
    }

    protected HentoidDB getDB() {
        return ((HentoidActivity) getActivity()).getDB();
    }

    protected HentoidActivity getHentoidActivity() {
        return (HentoidActivity) getActivity();
    }
}