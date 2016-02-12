package me.devsaki.hentoid.components;

import android.app.ListFragment;

import me.devsaki.hentoid.database.HentoidDB;

/**
 * Created by neko on 06/06/2015.
 */

public abstract class HentoidFragment extends ListFragment {

    protected HentoidDB getDB() {
        return ((HentoidActivity) getActivity()).getDB();
    }

}