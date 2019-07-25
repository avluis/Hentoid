package me.devsaki.hentoid.adapters;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.SiteAdapterViewHolder> {

    private View.OnClickListener onClickListener = null;
    private List<Site> dataset = new ArrayList<>();

    public void setOnClickListener(View.OnClickListener listener) {
        this.onClickListener = listener;
    }

    @NonNull
    @Override
    public SiteAdapterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_text_sites, parent, false);
        return new SiteAdapterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SiteAdapterViewHolder holder, int position) {
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

    static class SiteAdapterViewHolder extends RecyclerView.ViewHolder {

        final TextView textView;

        private SiteAdapterViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.drawer_item_txt);
        }

        void bindTo(Site site) {
            textView.setText(Helper.capitalizeString(site.getDescription()));
            textView.setTag(site);
        }
    }
}
