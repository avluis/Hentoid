package me.devsaki.hentoid.components;

import android.app.ListFragment;
import android.content.SharedPreferences;
import android.view.View;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.FakkuDroidDB;

/**
 * Created by neko on 06/06/2015.
 */

public abstract class HentoidFragment extends ListFragment {

    protected SharedPreferences getSharedPreferences(){
        return ((HentoidActivity) getActivity()).getSharedPreferences();
    }

    protected FakkuDroidDB getDB(){
        return ((HentoidActivity) getActivity()).getDB();
    }

    protected HentoidActivity getFakkuDroidActivity(){
        return (HentoidActivity) getActivity();
    }

    public void showLoading(){
        getView().findViewById(R.id.content_main).setVisibility(View.GONE);
        getView().findViewById(R.id.content_loading).setVisibility(View.VISIBLE);
    }

    public void hideLoading(){
        getView().findViewById(R.id.content_loading).setVisibility(View.GONE);
        getView().findViewById(R.id.content_main).setVisibility(View.VISIBLE);
    }
}
