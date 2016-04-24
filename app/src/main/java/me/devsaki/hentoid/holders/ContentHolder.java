package me.devsaki.hentoid.holders;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import me.devsaki.hentoid.R;

/**
 * Created by avluis on 04/23/2016.
 * <p/>
 * TODO: Add tvSite, tvStatus, tvSavedDate
 */
public class ContentHolder extends RecyclerView.ViewHolder {
    public final TextView tvTitle;
    public final ImageView ivCover;
    public final TextView tvSeries;
    public final TextView tvArtist;
    public final TextView tvTags;
//    public final TextView tvSite;
//    public final TextView tvStatus;
//    public final TextView tvSavedDate;

    public ContentHolder(final View itemView) {
        super(itemView);

        tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
        ivCover = (ImageView) itemView.findViewById(R.id.ivCover);
        tvSeries = (TextView) itemView.findViewById(R.id.tvSeries);
        tvArtist = (TextView) itemView.findViewById(R.id.tvArtist);
        tvTags = (TextView) itemView.findViewById(R.id.tvTags);
//        tvSite = (TextView) itemView.findViewById(R.id.tvSite);
//        tvStatus = (TextView) itemView.findViewById(R.id.tvStatus);
//        tvSavedDate = (TextView) itemView.findViewById(R.id.tvSavedDate);
    }
}