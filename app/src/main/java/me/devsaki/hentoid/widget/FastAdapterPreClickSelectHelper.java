package me.devsaki.hentoid.widget;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mikepenz.fastadapter.IAdapter;
import com.mikepenz.fastadapter.IItem;
import com.mikepenz.fastadapter.select.SelectExtension;

import java.util.Set;

@SuppressWarnings({"unused", "java:S1172"})
public class FastAdapterPreClickSelectHelper<T extends IItem<? extends RecyclerView.ViewHolder>> {

    private final SelectExtension<T> selectExtension;

    public FastAdapterPreClickSelectHelper(@NonNull SelectExtension<T> selectExtension) {
        this.selectExtension = selectExtension;
    }

    public Boolean onPreClickListener(View v, IAdapter<T> adapter, T item, Integer position) {
        Set<Integer> selectedPositions = selectExtension.getSelections();
        // Toggle selection while select mode is on
        if (!selectExtension.getSelectOnLongClick() && !selectedPositions.isEmpty()) {
            if (selectedPositions.contains(position) && 1 == selectedPositions.size())
                selectExtension.setSelectOnLongClick(true);
            selectExtension.toggleSelection(position);
            return true;
        }
        return false;
    }

    public Boolean onPreLongClickListener(View v, IAdapter<T> adapter, T item, Integer position) {
        Set<Integer> selectedPositions = selectExtension.getSelections();
        if (selectedPositions.isEmpty() && selectExtension.getSelectOnLongClick()) { // No selection -> select things
            selectExtension.select(position);
            selectExtension.setSelectOnLongClick(false);
            return true;
        }
        return false;
    }
}
