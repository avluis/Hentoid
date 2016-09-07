package me.devsaki.hentoid.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import me.devsaki.hentoid.R;

/**
 * Created by avluis on 04/23/2016.
 * Content View Holder for Downloads Content Adapter
 * <p/>
 * TODO: Research if possible to re-use widget id (to eliminate duplication)
 */
class ContentHolder extends RecyclerView.ViewHolder {

    final TextView tvTitle;
    final TextView tvTitle2;
    final ImageView ivCover;
    final ImageView ivCover2;
    final TextView tvSeries;
    final TextView tvArtist;
    final TextView tvTags;
    final ImageView ivSite;
    final ImageView ivError;

    ContentHolder(final View itemView) {
        super(itemView);

        tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
        tvTitle2 = (TextView) itemView.findViewById(R.id.tvTitle2);
        ivCover = (ImageView) itemView.findViewById(R.id.ivCover);
        ivCover2 = (ImageView) itemView.findViewById(R.id.ivCover2);
        tvSeries = (TextView) itemView.findViewById(R.id.tvSeries);
        tvArtist = (TextView) itemView.findViewById(R.id.tvArtist);
        tvTags = (TextView) itemView.findViewById(R.id.tvTags);
        ivSite = (ImageView) itemView.findViewById(R.id.ivSite);
        ivError = (ImageView) itemView.findViewById(R.id.ivError);

        itemView.setClickable(true);
    }
}
