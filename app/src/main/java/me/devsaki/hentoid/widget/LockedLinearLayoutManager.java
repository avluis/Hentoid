package me.devsaki.hentoid.widget;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class LockedLinearLayoutManager extends LinearLayoutManager {

    private int visibleItems = -1;


    public LockedLinearLayoutManager(Context context) {
        super(context);
    }

    public void setVisibleItems(int visibleItems) {
        this.visibleItems = visibleItems;
    }


}
