package me.devsaki.hentoid.viewholders;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
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

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * ViewHolder for Content
 */
public class ContentHolder extends RecyclerView.ViewHolder {

    private final static RequestOptions glideRequestOptions = new RequestOptions()
            .centerInside()
            .error(R.drawable.ic_placeholder);

    private final View baseLayout;
    private final TextView tvTitle;
    private final View ivNew;
    private final ImageView ivCover;
    private final TextView tvSeries;
    private final TextView tvArtist;
    private final TextView tvPages;
    private final TextView tvTags;
    private final ImageView ivSite;
    private final ImageView ivError;
    private final ImageView ivFavourite;

    // Corresponding content
    // NB : When using PagedListAdapter, this does _not_ always match the freshest instance
    // of the object stored in the adapter since it is constantly updated as data evolves,
    // and does not trigger binding for each of these updates
    // (binding only happens when the content is different / see DiffUtil)
    private Content content;
    // Containing adapter
    private LibraryAdapter adapter;


    public ContentHolder(@NonNull View view, LibraryAdapter adapter) {
        super(view);

        this.adapter = adapter;

        baseLayout = requireViewById(itemView, R.id.item);
        tvTitle = requireViewById(itemView, R.id.tvTitle);
        ivNew = requireViewById(itemView, R.id.lineNew);
        ivCover = requireViewById(itemView, R.id.ivCover);
        tvSeries = requireViewById(itemView, R.id.tvSeries);
        tvArtist = requireViewById(itemView, R.id.tvArtist);
        tvPages = requireViewById(itemView, R.id.tvPages);
        tvTags = requireViewById(itemView, R.id.tvTags);
        ivSite = requireViewById(itemView, R.id.ivSite);
        ivError = requireViewById(itemView, R.id.ivError);
        ivFavourite = requireViewById(itemView, R.id.ivFavourite);
    }

    public void bind(@NonNull Content content) {
        this.content = content;
        updateLayoutVisibility(content);
        attachCover(content);
        attachTitle(content);
        attachSeries(content);
        attachArtist(content);
        attachPages(content);
        attachTags(content);
        attachButtons(content);
        attachOnClickListeners();
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
            String artists = android.text.TextUtils.join(", ", allArtists);
            tvArtist.setText(templateArtist.replace("@artist@", artists));
        }
    }

    private void attachPages(Content content) {
        Context context = tvPages.getContext();
        String template = context.getResources().getString(R.string.work_pages);
        tvPages.setText(template.replace("@pages@", content.getQtyPages() + ""));
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

    private void attachButtons(final Content content) {
        // Source icon
        if (content.getSite() != null) {
            int img = content.getSite().getIco();
            ivSite.setImageResource(img);
            ivSite.setOnClickListener(v -> onSourceClicked());
        } else {
            ivSite.setImageResource(R.drawable.ic_stat_hentoid);
        }

        // Favourite icon
        ivFavourite.setOnClickListener(v -> onFavouriteClicked());

        // When transitioning to the other state, button blinks with its target state
        if (content.isBeingFavourited()) {
            ivFavourite.startAnimation(new BlinkAnimation(500, 250));
            if (content.isFavourite()) {
                ivFavourite.setImageResource(R.drawable.ic_fav_empty);
            } else {
                ivFavourite.setImageResource(R.drawable.ic_fav_full);
            }
        } else {
            ivFavourite.clearAnimation();
            if (content.isFavourite()) {
                ivFavourite.setImageResource(R.drawable.ic_fav_full);
            } else {
                ivFavourite.setImageResource(R.drawable.ic_fav_empty);
            }
        }

        // Error icon
        ivError.setOnClickListener(v -> onErrorClicked());
        if (content.getStatus() != null) {
            StatusContent status = content.getStatus();
            if (status == StatusContent.ERROR) {
                ivError.setVisibility(View.VISIBLE);
            } else {
                ivError.setVisibility(View.GONE);
            }
        }
    }

    // NB : There's only one listener instantiated in the fragment and consuming the corresponding Content

    private void onSourceClicked() {
        adapter.getOnSourceClickListener().accept(content);
    }

    private void onFavouriteClicked() {
        adapter.getFavClickListener().accept(content);
        // Hack to make fav icon blink with PagedListAdapter by force-rebinding the holder
        // Call is delayed to give time for the adapter list to be updated by LiveData
        new Handler().postDelayed(() -> adapter.notifyItemChanged(getLayoutPosition()), 100);
    }

    private void onErrorClicked() {
        adapter.getErrorClickListener().accept(content);
    }

    private void attachOnClickListeners() {

        // Simple click
        itemView.setOnClickListener(v -> {
            if (adapter.getSelectedItemsCount() > 0) { // Selection mode is on -> select more
                int itemPos = getLayoutPosition();
                if (itemPos > -1) {
                    Content c = adapter.getItemAtPosition(itemPos); // Get the freshest content from the adapter
                    if (c != null) {
                        c.setSelected(!c.isSelected());
                        adapter.getSelectionChangedListener().accept(adapter.getSelectedItemsCount());
                        adapter.notifyItemChanged(itemPos);
                    }
                }
            } else adapter.getOpenBookListener().accept(content); // Open book
        });

        // Long click = select item
        itemView.setOnLongClickListener(v -> {

            int itemPos = getLayoutPosition();
            Content c = adapter.getItemAtPosition(itemPos); // Get the freshest content from the adapter

            if (c != null && itemPos > -1 && !c.isBeingDeleted()) {
                c.setSelected(!c.isSelected());
                adapter.getSelectionChangedListener().accept(adapter.getSelectedItemsCount());
                adapter.notifyItemChanged(itemPos);
            }

            return true;
        });
    }
}
