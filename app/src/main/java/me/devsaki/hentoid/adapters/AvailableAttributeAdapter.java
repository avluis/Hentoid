package me.devsaki.hentoid.adapters;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.viewholders.AttributeViewHolder;

public class AvailableAttributeAdapter extends RecyclerView.Adapter<AttributeViewHolder> {

    private static final int VISIBLE_THRESHOLD = 5;

    private List<Attribute> dataset = new ArrayList<>();
    private Runnable onScrollToEndListener = null;
    private View.OnClickListener onClickListener = null;

    @NonNull
    @Override
    public AttributeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chip_choice, parent, false);
        return new AttributeViewHolder(view);
    }

    public void setOnScrollToEndListener(Runnable listener) {
        this.onScrollToEndListener = listener;
    }

    public void setOnClickListener(View.OnClickListener listener) {
        this.onClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull AttributeViewHolder holder, int position) {
        if (position == getItemCount() - VISIBLE_THRESHOLD && onScrollToEndListener != null) {
            onScrollToEndListener.run();
        }
        holder.bindTo(dataset.get(position));
        holder.itemView.setOnClickListener(onClickListener);
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }

    public void add(List<Attribute> contents) {
        dataset.addAll(contents);
        notifyDataSetChanged();
    }

    public void clear() {
        dataset.clear();
        notifyDataSetChanged();
    }
}
