package me.devsaki.hentoid.listener;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 05/07/2016.
 * Item OnClick and OnLongClick Listener
 */
public class ItemClickListener implements OnClickListener, OnLongClickListener {
    private static final String TAG = LogHelper.makeLogTag(ItemClickListener.class);

    private final Context context;
    private final Content content;
    private final ItemSelectListener listener;
    private final int position;
    private int selectedItemCount;
    private boolean selected;

    public ItemClickListener(Context cxt, Content content, int pos, ItemSelectListener listener) {
        this.context = cxt;
        this.content = content;
        this.position = pos;
        this.selectedItemCount = 0;
        this.listener = listener;
    }

    public void setSelected(boolean selected, int selectedItemCount) {
        this.selected = selected;
        this.selectedItemCount = selectedItemCount;
    }

//    public void clearAndSelect(List<Content> contents, int selectedItem) {
//        int currentItem = this.selectedItem;
//        if (currentItem != selectedItem) {
//            AndroidHelper.toast(context, "Coming in the next update!");
//            LogHelper.d(TAG, "Clear: " + "Position: " + this.selectedItem + ": "
//                    + contents.get(currentItem).getTitle());
//            LogHelper.d(TAG, "Select: " + "Position: " + selectedItem + ": "
//                    + contents.get(selectedItem).getTitle());
//        }
//    }

    private void matchAndClear(int position) {
        if (selected) {
            listener.onItemSelected(selectedItemCount, position);
        } else {
            listener.onItemClear(selectedItemCount, position);
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

        matchAndClear(position);

        return true;
    }

    public interface ItemSelectListener {
        void onItemSelected(int selectedCount, int position);

        void onItemClear(int itemCount, int position);
    }
}