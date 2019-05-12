package me.devsaki.hentoid.listener;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.FileHelper;
import timber.log.Timber;

/**
 * Created by avluis on 05/07/2016.
 * Item OnClick and OnLongClick Listener with support for item selection
 */
public class ContentClickListener implements OnClickListener, OnLongClickListener {

    // TODO - rework this class : each time it is used, either onClick or onLongClick are overriden

    private final Context context;
    private final Content content;
    private final ItemSelectListener listener;
    private final int position;
    private int selectedItemCount;
    private boolean selected;

    protected ContentClickListener(Context context, Content content, int pos, ItemSelectListener listener) {
        this.context = context;
        this.content = content;
        this.position = pos;
        this.selectedItemCount = 0;
        this.listener = listener;
    }

    protected void setSelected(boolean selected, int selectedItemCount) {
        this.selected = selected;
        this.selectedItemCount = selectedItemCount;
    }

    private void updateSelector() {
        if (selected) {
            listener.onItemSelected(selectedItemCount);
        } else {
            listener.onItemClear(selectedItemCount);
        }
    }

    @Override
    public void onClick(View v) {
        if (!selected) {
            FileHelper.openContent(context, content);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        updateSelector();

        Timber.d("Position: %s %s has been %s",
                position, content.getTitle(), (selected ? "selected." : "unselected."));

        return true;
    }

    public interface ItemSelectListener {
        void onItemSelected(int selectedCount);

        void onItemClear(int itemCount);
    }
}
