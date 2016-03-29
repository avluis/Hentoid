package me.devsaki.hentoid.fragments;

import android.app.ListFragment;

import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.database.HentoidDB;

/**
 * Created by neko on 06/06/2015.
 * Builds BaseFragment from ListFragment
 */

public abstract class BaseFragment extends ListFragment {

    protected HentoidDB getDB() {
        return ((BaseActivity) getActivity()).getDB();
    }

}