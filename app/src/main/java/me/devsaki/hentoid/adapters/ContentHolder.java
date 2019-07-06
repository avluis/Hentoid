package me.devsaki.hentoid.adapters;

import androidx.recyclerview.widget.RecyclerView;
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

    final View fullLayout;
    final View miniLayout;
    final TextView tvTitle;
    final TextView tvTitle2;
    final ImageView ivNew;
    final ImageView ivCover;
    final ImageView ivCover2;
    final TextView tvSeries;
    final TextView tvArtist;
    final TextView tvTags;
    final ImageView ivSite;
    final ImageView ivError;
    final ImageView ivFavourite;
    final ImageView ivDownload; // Mikan mode only

    ContentHolder(final View itemView) {
        super(itemView);

        fullLayout = itemView.findViewById(R.id.item);
        miniLayout = itemView.findViewById(R.id.item_minimal);
        tvTitle = itemView.findViewById(R.id.tvTitle);
        ivNew = itemView.findViewById(R.id.iconNew);
        tvTitle2 = itemView.findViewById(R.id.tvTitle2);
        ivCover = itemView.findViewById(R.id.ivCover);
        ivCover2 = itemView.findViewById(R.id.ivCover2);
        tvSeries = itemView.findViewById(R.id.tvSeries);
        tvArtist = itemView.findViewById(R.id.tvArtist);
        tvTags = itemView.findViewById(R.id.tvTags);
        ivSite = itemView.findViewById(R.id.ivSite);
        ivError = itemView.findViewById(R.id.ivError);
        ivFavourite = itemView.findViewById(R.id.ivFavourite);
        ivDownload = itemView.findViewById(R.id.ivDownload);
    }
}
