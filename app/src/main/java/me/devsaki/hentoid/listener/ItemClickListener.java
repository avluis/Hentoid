package me.devsaki.hentoid.listener;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 05/07/2016.
 * Item Click
 */
public class ItemClickListener implements OnClickListener, OnLongClickListener {
    private static final String TAG = LogHelper.makeLogTag(ItemClickListener.class);

    private final Context context;
    private final Content content;
    private final int position;
    private int selectedItem;
    private boolean selected;

    public ItemClickListener(Context context, Content content, int pos) {
        this.context = context;
        this.content = content;
        this.position = pos;
        this.selectedItem = -1;
    }

    public void setSelected(boolean selected, int selectedItem) {
        this.selected = selected;
        this.selectedItem = selectedItem;
    }

    public void clearAndSelect(List<Content> contents, int selectedItem) {
        int currentItem = this.selectedItem;
        if (currentItem != selectedItem) {
            AndroidHelper.toast(context, "Not yet implemented!");
            LogHelper.d(TAG, "Clear: " + "Position: " + this.selectedItem + ": "
                    + contents.get(currentItem).getTitle());
            LogHelper.d(TAG, "Select: " + "Position: " + selectedItem + ": "
                    + contents.get(selectedItem).getTitle());
        }
    }

    @Override
    public void onClick(View v) {
        if (!selected) {
            AndroidHelper.toast(context, "Opening: " + content.getTitle());
            AndroidHelper.openContent(context, content);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        LogHelper.d(TAG, "Position: " + position + ": " + content.getTitle() +
                " has been" + (selected ? " selected." : " unselected."));

        return true;
    }
}