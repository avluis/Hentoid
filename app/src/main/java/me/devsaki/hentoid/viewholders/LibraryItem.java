package me.devsaki.hentoid.viewholders;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.adapters.LibraryAdapter;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.ContentHelper;

public class LibraryItem extends RecyclerView.ViewHolder {

    private final static RequestOptions glideRequestOptions = new RequestOptions()
            .centerInside()
            .error(R.drawable.ic_placeholder);

    private final View baseLayout;
    private final TextView tvTitle;
    private final View ivNew;
    private final ImageView ivCover;
    private final TextView tvSeries;
    private final TextView tvArtist;
    private final TextView tvTags;
    private final ImageView ivSite;
    private final ImageView ivError;
    private final ImageView ivFavourite;

    private Content content;
    private LibraryAdapter adapter;


    public LibraryItem(@NonNull View view, LibraryAdapter adapter) {
        super(view);

        this.adapter = adapter;

        baseLayout = itemView.findViewById(R.id.item);
        tvTitle = itemView.findViewById(R.id.tvTitle);
        ivNew = itemView.findViewById(R.id.lineNew);
        ivCover = itemView.findViewById(R.id.ivCover);
        tvSeries = itemView.findViewById(R.id.tvSeries);
        tvArtist = itemView.findViewById(R.id.tvArtist);
        tvTags = itemView.findViewById(R.id.tvTags);
        ivSite = itemView.findViewById(R.id.ivSite);
        ivError = itemView.findViewById(R.id.ivError);
        ivFavourite = itemView.findViewById(R.id.ivFavourite);
    }

    public void bind(@NonNull Content content) {
        this.content = content;
        updateLayoutVisibility(content);
        attachCover(content);
        attachTitle(content);
        attachSeries(content);
        attachArtist(content);
        attachTags(content);
        attachButtons(content);
        attachOnClickListeners(content);
    }

    public void clear() {
        this.content = null;
        baseLayout.setVisibility(View.GONE);
    }

    private void updateLayoutVisibility(Content content) {
        baseLayout.setVisibility(View.VISIBLE);
        ivNew.setVisibility((0 == content.getReads()) ? View.VISIBLE : View.GONE);

        itemView.setSelected(content.isSelected());

        ivError.setEnabled(!content.isSelected());
        ivFavourite.setEnabled(!content.isSelected());
        ivSite.setEnabled(!content.isSelected());

        if (content.isBeingDeleted())
            baseLayout.startAnimation(new BlinkAnimation(500, 250));
        else
            baseLayout.clearAnimation();

        if (content.isBeingFavourited())
            ivFavourite.startAnimation(new BlinkAnimation(500, 250));
        else
            ivFavourite.clearAnimation();
    }

    private void attachCover(Content content) {
        Context context = ivCover.getContext();
        Glide.with(context.getApplicationContext())
                .load(ContentHelper.getThumb(content))
                .apply(glideRequestOptions)
                .into(ivCover);
    }

    private void attachTitle(Content content) {
        CharSequence title;
        Context context = tvTitle.getContext();
        if (content.getTitle() == null) {
            title = context.getText(R.string.work_untitled);
        } else {
            title = content.getTitle();
        }
        tvTitle.setText(title);
    }

    private void attachSeries(Content content) {
        Context context = tvSeries.getContext();
        String templateSeries = context.getResources().getString(R.string.work_series);
        List<Attribute> seriesAttributes = content.getAttributeMap().get(AttributeType.SERIE);
        if (seriesAttributes == null) {
            tvSeries.setText(templateSeries.replace("@series@", context.getResources().getString(R.string.work_untitled)));
        } else {
            StringBuilder seriesBuilder = new StringBuilder();
            for (int i = 0; i < seriesAttributes.size(); i++) {
                Attribute attribute = seriesAttributes.get(i);
                seriesBuilder.append(attribute.getName());
                if (i != seriesAttributes.size() - 1) {
                    seriesBuilder.append(", ");
                }
            }
            tvSeries.setText(templateSeries.replace("@series@", seriesBuilder));
        }
    }

    private void attachArtist(Content content) {
        Context context = tvArtist.getContext();
        String templateArtist = context.getResources().getString(R.string.work_artist);
        List<Attribute> attributes = new ArrayList<>();

        List<Attribute> artistAttributes = content.getAttributeMap().get(AttributeType.ARTIST);
        if (artistAttributes != null)
            attributes.addAll(artistAttributes);
        List<Attribute> circleAttributes = content.getAttributeMap().get(AttributeType.CIRCLE);
        if (circleAttributes != null)
            attributes.addAll(circleAttributes);

        if (attributes.isEmpty()) {
            tvArtist.setText(templateArtist.replace("@artist@", context.getResources().getString(R.string.work_untitled)));
        } else {
            List<String> allArtists = new ArrayList<>();
            for (Attribute attribute : attributes) {
                allArtists.add(attribute.getName());
            }
            String artists = android.text.TextUtils.join(",", allArtists);
            tvArtist.setText(templateArtist.replace("@artist@", artists));
        }
    }

    private void attachTags(Content content) {
        Context context = tvTags.getContext();
        List<Attribute> tagsAttributes = content.getAttributeMap().get(AttributeType.TAG);
        if (tagsAttributes == null) {
            tvTags.setText(context.getResources().getString(R.string.work_untitled));
        } else {
            List<String> allTags = new ArrayList<>();
            for (Attribute attribute : tagsAttributes) {
                allTags.add(attribute.getName());
            }
            if (Build.VERSION.SDK_INT >= 24) {
                allTags.sort(null);
            }
            String tags = android.text.TextUtils.join(", ", allTags);
            tvTags.setText(tags);
        }
    }

    private void onSourceClicked(View v) {
        adapter.getOnSourceClickListener().accept(content);
    }

    private void onFavouriteClicked(View v) {
        adapter.getFavClickListener().accept(content);
    }

    private void onErrorClicked(View v) {
        adapter.getErrorClickListener().accept(content);
    }

    private void attachButtons(final Content content) {
        // Source
        if (content.getSite() != null) {
            int img = content.getSite().getIco();
            ivSite.setImageResource(img);
            ivSite.setOnClickListener(this::onSourceClicked);
        } else {
            ivSite.setImageResource(R.drawable.ic_stat_hentoid);
        }

        // Favourite
        ivFavourite.setOnClickListener(this::onFavouriteClicked);
        if (content.isFavourite()) {
            ivFavourite.setImageResource(R.drawable.ic_fav_full);
        } else {
            ivFavourite.setImageResource(R.drawable.ic_fav_empty);
        }

        // Error icon
        ivError.setOnClickListener(this::onErrorClicked);
        if (content.getStatus() != null) {
            StatusContent status = content.getStatus();
            if (status == StatusContent.ERROR) {
                ivError.setVisibility(View.VISIBLE);
            } else {
                ivError.setVisibility(View.GONE);
            }
        }
    }

    private void attachOnClickListeners(@NonNull Content content) {

        // Simple click
        itemView.setOnClickListener(v -> {
            if (adapter.getSelectedItemsCount() > 0) { // Selection mode is on -> select more
                int itemPos = getLayoutPosition();
                if (getLayoutPosition() > -1) {
                    content.setSelected(!content.isSelected());
                    adapter.getSelectionChangedListener().accept(adapter.getSelectedItemsCount());
                    adapter.notifyItemChanged(itemPos);
                }
            } else adapter.getOpenBookListener().accept(content); // Open book
        });

        // Long click = select item
        itemView.setOnLongClickListener(v -> {

            int itemPos = getLayoutPosition();
            if (itemPos > -1 && !content.isBeingDeleted()) {
                content.setSelected(!content.isSelected());
                adapter.getSelectionChangedListener().accept(adapter.getSelectedItemsCount());
                adapter.notifyItemChanged(itemPos);
            }

            return true;
        });
    }
}
