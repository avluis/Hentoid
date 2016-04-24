package me.devsaki.hentoid.holders;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.listener.ItemClickListener;
import me.devsaki.hentoid.listener.ItemLongClickListener;

/**
 * Created by avluis on 04/23/2016.
 * <p/>
 * TODO: Add tvSite, tvStatus, tvSavedDate
 */
public class ContentHolder extends RecyclerView.ViewHolder implements
        OnClickListener, OnLongClickListener {
    public final TextView tvTitle;
    public final ImageView ivCover;
    public final TextView tvSeries;
    public final TextView tvArtist;
    public final TextView tvTags;
//    public final TextView tvSite;
//    public final TextView tvStatus;
//    public final TextView tvSavedDate;

    private ItemClickListener mClickListener;
    private ItemLongClickListener mLongClickListener;

    public ContentHolder(final View itemView,
                         ItemClickListener clickListener, ItemLongClickListener longClickListener) {
        super(itemView);

        tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
        ivCover = (ImageView) itemView.findViewById(R.id.ivCover);
        tvSeries = (TextView) itemView.findViewById(R.id.tvSeries);
        tvArtist = (TextView) itemView.findViewById(R.id.tvArtist);
        tvTags = (TextView) itemView.findViewById(R.id.tvTags);
//        tvSite = (TextView) itemView.findViewById(R.id.tvSite);
//        tvStatus = (TextView) itemView.findViewById(R.id.tvStatus);
//        tvSavedDate = (TextView) itemView.findViewById(R.id.tvSavedDate);

        this.mClickListener = clickListener;
        this.mLongClickListener = longClickListener;

        itemView.setOnClickListener(this);
        itemView.setOnLongClickListener(this);

        itemView.setClickable(true);
    }

    @Override
    public void onClick(View v) {
        if (mClickListener != null) {
            mClickListener.onItemClick(v, getLayoutPosition());
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (mLongClickListener != null) {
            mLongClickListener.onItemLongClick(v, getLayoutPosition());
        }
        return true;
    }
}