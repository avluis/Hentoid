package me.devsaki.hentoid.viewholders;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ContentItemBundle;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.HttpHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.ThemeHelper;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

public class ContentItem extends AbstractItem<ContentItem.ContentViewHolder> {

    private final static RequestOptions glideRequestOptions = new RequestOptions()
            .centerInside()
            .error(R.drawable.ic_placeholder);

    private Content content;
    private boolean isQueued;
    private boolean isEmpty;

    // Constructor for empty placeholder
    public ContentItem(boolean isQueued) {
        isEmpty = true;
        content = null;
        this.isQueued = isQueued;
    }

    // Constructor for library item
    public ContentItem(@NonNull Content content) {
        this.content = content;
        isQueued = false;
        setIdentifier(content.getId());
        setSelectable(!isQueued);
        isEmpty = false;
    }

    // Constructor for queued item
    public ContentItem(@NonNull QueueRecord content) {
        this.content = content.content.getTarget();
        isQueued = true;
        setIdentifier(this.content.getId());
        setSelectable(!isQueued);
        isEmpty = false;
    }

    public Content getContent() {
        return content;
    }

    @NotNull
    @Override
    public ContentItem.ContentViewHolder getViewHolder(@NotNull View view) {
        return new ContentViewHolder(view, isQueued);
    }

    @Override
    public int getLayoutRes() {
        return isQueued ? R.layout.item_queue : R.layout.item_download;
    }

    @Override
    public int getType() {
        return R.id.content;
    }


    public static class ContentViewHolder extends FastAdapter.ViewHolder<ContentItem> {

        // Common elements
        private View baseLayout;
        private TextView tvTitle;
        private ImageView ivCover;
        private TextView tvSeries;
        private TextView tvArtist;
        private TextView tvPages;
        private TextView tvTags;
        private ImageView ivSite;

        // Specific to library content
        private View ivNew;
        private ImageView ivError;
        private ImageView ivFavourite;

        // Specific to Queued content
        private ProgressBar pbDownload;
        private ImageView ivTop;
        private ImageView ivUp;
        private ImageView ivDown;
        private View ivCancel;


        ContentViewHolder(View view, boolean isQueued) {
            super(view);

            baseLayout = requireViewById(itemView, R.id.item);
            tvTitle = requireViewById(itemView, R.id.tvTitle);
            ivCover = requireViewById(itemView, R.id.ivCover);
            tvSeries = requireViewById(itemView, R.id.tvSeries);
            tvArtist = requireViewById(itemView, R.id.tvArtist);
            tvPages = requireViewById(itemView, R.id.tvPages);
            tvTags = requireViewById(itemView, R.id.tvTags);
            ivSite = requireViewById(itemView, R.id.ivSite);
            view.setBackground(ThemeHelper.makeCardSelector(view.getContext()));

            if (!isQueued) {
                ivNew = itemView.findViewById(R.id.lineNew);
                ivError = itemView.findViewById(R.id.ivError);
                ivFavourite = itemView.findViewById(R.id.ivFavourite);
            } else {
                pbDownload = itemView.findViewById(R.id.pbDownload);
                ivTop = itemView.findViewById(R.id.queueTopBtn);
                ivUp = itemView.findViewById(R.id.queueUpBtn);
                ivDown = itemView.findViewById(R.id.queueDownBtn);
                ivCancel = itemView.findViewById(R.id.btnCancel);
            }
        }


        @Override
        public void bindView(@NotNull ContentItem item, @NotNull List<Object> payloads) {
            if (item.isEmpty || null == item.content) return; // Ignore placeholders from PagedList

            // Payloads are set when the content stays the same but some properties alone change
            if (!payloads.isEmpty()) {
                Bundle bundle = (Bundle) payloads.get(0);
                ContentItemBundle.Parser bundleParser = new ContentItemBundle.Parser(bundle);

                Boolean boolValue = bundleParser.isBeingFavourited();
                if (boolValue != null) item.content.setIsBeingFavourited(boolValue);
                boolValue = bundleParser.isBeingDeleted();
                if (boolValue != null) item.content.setIsBeingDeleted(boolValue);
                boolValue = bundleParser.isFavourite();
                if (boolValue != null) item.content.setFavourite(boolValue);
                Long longValue = bundleParser.getReads();
                if (longValue != null) item.content.setReads(longValue);
            }

            updateLayoutVisibility(item);
            attachCover(item.content);
            attachTitle(item.content);
            attachArtist(item.content);
            attachSeries(item.content);
            attachPages(item.content, item.isQueued);
            attachTags(item.content);
            attachButtons(item);
            if (item.isQueued)
                updateProgress(item.content, pbDownload, getAdapterPosition(), false);
        }

        private void updateLayoutVisibility(ContentItem item) {
            baseLayout.setVisibility(item.isEmpty ? View.GONE : View.VISIBLE);
            if (item.getContent().isBeingDeleted())
                baseLayout.startAnimation(new BlinkAnimation(500, 250));
            else
                baseLayout.clearAnimation();
            if (!item.isQueued)
                ivNew.setVisibility((0 == item.getContent().getReads()) ? View.VISIBLE : View.GONE);
        }

        private void attachCover(Content content) {
            String thumbLocation = ContentHelper.getThumb(content);
            Context context = ivCover.getContext();

            // Use content's cookies to load image (useful for ExHentai when viewing queue screen)
            if (thumbLocation.startsWith("http")
                    && content.getDownloadParams() != null
                    && content.getDownloadParams().length() > 2 // Avoid empty and "{}"
                    && content.getDownloadParams().contains(HttpHelper.HEADER_COOKIE_KEY)) {

                Map<String, String> downloadParams = null;
                try {
                    downloadParams = JsonHelper.jsonToObject(content.getDownloadParams(), JsonHelper.MAP_STRINGS);
                } catch (IOException e) {
                    Timber.w(e);
                }

                if (downloadParams != null && downloadParams.containsKey(HttpHelper.HEADER_COOKIE_KEY)) {
                    String cookiesStr = downloadParams.get(HttpHelper.HEADER_COOKIE_KEY);
                    if (cookiesStr != null) {
                        LazyHeaders.Builder builder = new LazyHeaders.Builder()
                                .addHeader(HttpHelper.HEADER_COOKIE_KEY, cookiesStr);

                        GlideUrl glideUrl = new GlideUrl(thumbLocation, builder.build());
                        Glide.with(context.getApplicationContext())
                                .load(glideUrl)
                                .apply(glideRequestOptions)
                                .into(ivCover);
                        return;
                    }
                }
            }

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
            tvTitle.setTextColor(ThemeHelper.getColor(tvTitle.getContext(), R.color.card_title_light));
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


        private void attachSeries(Content content) {
            Context context = tvSeries.getContext();
            String templateSeries = context.getResources().getString(R.string.work_series);
            List<Attribute> seriesAttributes = content.getAttributeMap().get(AttributeType.SERIE);
            if (seriesAttributes == null || seriesAttributes.isEmpty()) {
                tvSeries.setVisibility(View.GONE);
                tvSeries.setText(templateSeries.replace("@series@", context.getResources().getString(R.string.work_untitled)));
            } else {
                tvSeries.setVisibility(View.VISIBLE);
                List<String> allSeries = new ArrayList<>();
                for (Attribute attribute : seriesAttributes) {
                    allSeries.add(attribute.getName());
                }
                String series = android.text.TextUtils.join(", ", allSeries);
                tvSeries.setText(templateSeries.replace("@series@", series));
            }
        }

        private void attachPages(Content content, boolean isQueued) {
            Context context = tvPages.getContext();
            String template = context.getResources().getString(R.string.work_pages);
            template = template.replace("@pages@", content.getQtyPages() + "");
            long nbMissingPages = content.getQtyPages() - content.getNbDownloadedPages();
            if (nbMissingPages > 0 && !isQueued)
                template = template.replace("@missing@", " (" + nbMissingPages + " missing)");
            else
                template = template.replace("@missing@", "");

            tvPages.setText(template);
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
                tvTags.setTextColor(ThemeHelper.getColor(tvTags.getContext(), R.color.card_tags_light));
            }
        }

        private void attachButtons(final ContentItem item) {
            Content content = item.getContent();

            // Source icon
            if (content.getSite() != null) {
                int img = content.getSite().getIco();
                ivSite.setImageResource(img);
            } else {
                ivSite.setImageResource(R.drawable.ic_stat_hentoid);
            }

            if (item.isQueued) {
                boolean isFirstItem = (0 == getAdapterPosition());
                /*
                int itemCount = item.adapter.getAdapterItemCount();
                boolean isLastItem = itemCount - 1 == getAdapterPosition();
                 */

                ivUp.setImageResource(R.drawable.ic_arrow_up);
                ivUp.setVisibility(isFirstItem ? View.INVISIBLE : View.VISIBLE);

                ivTop.setImageResource(R.drawable.ic_doublearrowup);
                ivTop.setVisibility((isFirstItem /*|| itemCount < 3*/) ? View.INVISIBLE : View.VISIBLE);

                ivDown.setImageResource(R.drawable.ic_arrow_down);
                ivDown.setVisibility(/*isLastItem ? View.INVISIBLE :*/ View.VISIBLE);
            } else {
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
                if (content.getStatus() != null) {
                    StatusContent status = content.getStatus();
                    if (status == StatusContent.ERROR) {
                        ivError.setVisibility(View.VISIBLE);
                    } else {
                        ivError.setVisibility(View.GONE);
                    }
                    ImageViewCompat.setImageTintList(ivError, ColorStateList.valueOf(ThemeHelper.getColor(ivError.getContext(), R.color.card_surface_light)));
                }
            }
        }

        public static void updateProgress(@NonNull Content content, @NonNull ProgressBar pb, int position, boolean isPausedEvent) {
            boolean isQueueReady = ContentQueueManager.getInstance().isQueueActive() && !ContentQueueManager.getInstance().isQueuePaused() && !isPausedEvent;
            boolean isFirstItem = (0 == position);

            content.computePercent();
            if ((isFirstItem && isQueueReady) || content.getPercent() > 0) {
                pb.setVisibility(View.VISIBLE);
                if (content.getPercent() > 0) {
                    pb.setIndeterminate(false);
                    pb.setProgress((int) content.getPercent());

                    int color;
                    if (isFirstItem && isQueueReady)
                        color = ThemeHelper.getColor(pb.getContext(), R.color.secondary_light);
                    else
                        color = ContextCompat.getColor(pb.getContext(), R.color.medium_gray);
                    pb.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
                } else {
                    pb.setIndeterminate(true);
                }
            } else {
                pb.setVisibility(View.GONE);
            }
        }

        public View getFavouriteButton() {
            return ivFavourite;
        }

        public View getSiteButton() {
            return ivSite;
        }

        public View getErrorButton() {
            return ivError;
        }

        public View getTopButton() {
            return ivTop;
        }

        public View getUpButton() {
            return ivUp;
        }

        public View getDownButton() {
            return ivDown;
        }

        public View getCancelButton() {
            return ivCancel;
        }

        @Override
        public void unbindView(@NotNull ContentItem item) {
            // Nothing to do here
        }
    }
}
