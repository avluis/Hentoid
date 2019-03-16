package me.devsaki.hentoid.adapters;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    private View.OnClickListener onClickListener = null;
    private List<Site> dataset = new ArrayList<>();

    public void setOnClickListener(View.OnClickListener listener) {
        this.onClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_picker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bindTo(dataset.get(position));
        holder.textView.setOnClickListener(onClickListener);
    }

    public int getItemCount() {
        return dataset.size();
    }

    public void add(List<Site> contents) {
        dataset.addAll(contents);
        notifyDataSetChanged();
    }

    public void clear() {
        dataset.clear();
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView textView;

        private ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.picker_item_name);
        }

        void bindTo(Site site) {
            textView.setText(Helper.capitalizeString(site.getDescription()));
            textView.setTag(site);
        }
    }
}
