package me.devsaki.hentoid.viewholders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.drag.IExtendedDraggable;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.swipe.ISwipeable;
import com.mikepenz.fastadapter.utils.DragDropUtil;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ContentItemBundle;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static me.devsaki.hentoid.util.ImageHelper.tintBitmap;

public class ContentItem extends AbstractItem<ContentItem.ContentViewHolder> implements IExtendedDraggable, ISwipeable {

    private static final RequestOptions glideRequestOptions;

    @IntDef({ViewType.LIBRARY, ViewType.QUEUE, ViewType.ERRORS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {
        int LIBRARY = 0;
        int QUEUE = 1;
        int ERRORS = 2;
    }

    private final Content content;
    private final @ViewType
    int viewType;
    private final boolean isEmpty;

    // Drag, drop & swipe
    private final ItemTouchHelper touchHelper;
    private int swipeDirection = 0;
    private boolean isSwipeable = true;
    private Runnable undoSwipeAction; // Action to run when hitting the "undo" button


    static {
        Context context = HentoidApp.getInstance();
        int tintColor = ThemeHelper.getColor(context, R.color.light_gray);

        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_hentoid_trans);
        Drawable d = new BitmapDrawable(context.getResources(), tintBitmap(bmp, tintColor));

        glideRequestOptions = new RequestOptions()
                .centerInside()
                .error(d);
    }

    // Constructor for empty placeholder
    public ContentItem(@ViewType int viewType) {
        isEmpty = true;
        content = null;
        this.viewType = viewType;
        touchHelper = null;
        setIdentifier(generateIdForPlaceholder());
    }

    // Constructor for library and error item
    public ContentItem(Content content, @Nullable ItemTouchHelper touchHelper, @ViewType int viewType) {
        this.content = content;
        this.viewType = viewType;
        this.touchHelper = touchHelper;
        isEmpty = (null == content);
        isSwipeable = (viewType == ViewType.ERRORS);
        if (content != null) setIdentifier(content.getId());
        else setIdentifier(generateIdForPlaceholder());
    }

    // Constructor for queued item
    public ContentItem(@NonNull QueueRecord record, ItemTouchHelper touchHelper) {
        content = record.content.getTarget();
        viewType = ViewType.QUEUE;
        this.touchHelper = touchHelper;
        isEmpty = (null == content);
//        setIdentifier(record.id);
        if (content != null) setIdentifier(content.getId());
        else setIdentifier(generateIdForPlaceholder());
    }

    @Nullable
    public Content getContent() {
        return content;
    }

    @NotNull
    @Override
    public ContentItem.ContentViewHolder getViewHolder(@NotNull View view) {
        return new ContentViewHolder(view, viewType);
    }

    @Override
    public int getLayoutRes() {
        if (ViewType.LIBRARY == viewType) return R.layout.item_library;
        else return R.layout.item_queue;
    }

    @Override
    public int getType() {
        return R.id.content;
    }

    @Override
    public boolean isDraggable() {
        return (ViewType.QUEUE == viewType);
    }

    @Override
    public boolean isSwipeable() {
        return isSwipeable;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public ItemTouchHelper getTouchHelper() {
        return touchHelper;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public View getDragView(@NotNull RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof ContentViewHolder)
            return ((ContentViewHolder) viewHolder).ivReorder;
        else return null;
    }

    public void setUndoSwipeAction(Runnable undoSwipeAction) {
        this.undoSwipeAction = undoSwipeAction;
    }

    public void setSwipeDirection(int direction) {
        swipeDirection = direction;
        isSwipeable = (0 == direction);
    }

    private long generateIdForPlaceholder() {
        long result = new Random().nextLong();
        // Make sure nothing collides with an actual ID; nobody has 1M books; it should be fine
        while (result < 1e6) result = new Random().nextLong();
        return result;
    }

    private void undoSwipe() {
        isSwipeable = true;
        undoSwipeAction.run();
    }


    public static class ContentViewHolder extends FastAdapter.ViewHolder<ContentItem> implements IDraggableViewHolder {

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
        private View swipeResult;
        private View bookCard;
        private ProgressBar progressBar;
        private View ivTop;
        private View ivBottom;
        private View ivReorder;
        private View tvUndoSwipe;
        private View ivRedownload;


        ContentViewHolder(View view, @ViewType int viewType) {
            super(view);

            baseLayout = requireViewById(itemView, R.id.item);
            tvTitle = requireViewById(itemView, R.id.tvTitle);
            ivCover = requireViewById(itemView, R.id.ivCover);
            tvArtist = requireViewById(itemView, R.id.tvArtist);
            tvPages = requireViewById(itemView, R.id.tvPages);
            ivSite = requireViewById(itemView, R.id.queue_site_button);
            ivError = itemView.findViewById(R.id.ivError);
            // Swipe elements
            swipeResult = itemView.findViewById(R.id.swipe_result_content);
            bookCard = itemView.findViewById(R.id.item_card);
            tvUndoSwipe = itemView.findViewById(R.id.undo_swipe);

            if (viewType == ViewType.LIBRARY) {
                ivNew = itemView.findViewById(R.id.lineNew);
                ivFavourite = itemView.findViewById(R.id.ivFavourite);
                tvSeries = requireViewById(itemView, R.id.tvSeries);
                tvTags = requireViewById(itemView, R.id.tvTags);
            } else if (viewType == ViewType.QUEUE) {
                progressBar = itemView.findViewById(R.id.pbDownload);
                ivTop = itemView.findViewById(R.id.queueTopBtn);
                ivBottom = itemView.findViewById(R.id.queueBottomBtn);
                ivReorder = itemView.findViewById(R.id.ivReorder);
            } else if (viewType == ViewType.ERRORS) {
                ivRedownload = itemView.findViewById(R.id.ivRedownload);
            }
        }

        @Override
        public void bindView(@NotNull ContentItem item, @NotNull List<?> payloads) {
            if (item.isEmpty || null == item.content) return; // Ignore placeholders from PagedList

            // Payloads are set when the content stays the same but some properties alone change
            if (!payloads.isEmpty()) {
                Bundle bundle = (Bundle) payloads.get(0);
                ContentItemBundle.Parser bundleParser = new ContentItemBundle.Parser(bundle);

                Boolean boolValue = bundleParser.isBeingDeleted();
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
            if (tvSeries != null)
                attachSeries(item.content);
            attachPages(item.content, item.viewType);
            attachButtons(item);
            if (tvTags != null)
                attachTags(item.content);
            if (progressBar != null)
                updateProgress(item.content, baseLayout, getAdapterPosition(), false);
            if (ivReorder != null)
                DragDropUtil.bindDragHandle(this, item);
            if (tvUndoSwipe != null)
                tvUndoSwipe.setOnClickListener(v -> item.undoSwipe());
        }

        private void updateLayoutVisibility(@NonNull final ContentItem item) {
            baseLayout.setVisibility(item.isEmpty ? View.GONE : View.VISIBLE);
            if (item.getContent() != null && item.getContent().isBeingDeleted())
                baseLayout.startAnimation(new BlinkAnimation(500, 250));
            else
                baseLayout.clearAnimation();

            // Unread indicator
            if (ivNew != null)
                ivNew.setVisibility((0 == item.getContent().getReads()) ? View.VISIBLE : View.GONE);

            // Queue swipe
            if (swipeResult != null) {
                bookCard.setVisibility((item.swipeDirection != 0) ? View.INVISIBLE : View.VISIBLE);
                swipeResult.setVisibility((item.swipeDirection != 0) ? View.VISIBLE : View.GONE);
            }
        }

        private void attachCover(@NonNull final Content content) {
            String thumbLocation = "";
            if (content.getCover().getStatus().equals(StatusContent.DOWNLOADED) || content.getCover().getStatus().equals(StatusContent.MIGRATED) || content.getCover().getStatus().equals(StatusContent.EXTERNAL))
                thumbLocation = content.getCover().getFileUri();
            if (thumbLocation.isEmpty()) thumbLocation = content.getCover().getUrl();
            if (thumbLocation.isEmpty()) thumbLocation = content.getCoverImageUrl();

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
                        Glide.with(ivCover)
                                .load(glideUrl)
                                .apply(glideRequestOptions)
                                .into(ivCover);
                        return;
                    }
                }
            }

            if (thumbLocation.startsWith("http"))
                Glide.with(ivCover)
                        .load(thumbLocation)
                        .apply(glideRequestOptions)
                        .into(ivCover);
            else
                Glide.with(ivCover)
                        .load(Uri.parse(thumbLocation))
                        .apply(glideRequestOptions)
                        .into(ivCover);
        }

        private void attachTitle(@NonNull final Content content) {
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

        private void attachArtist(@NonNull final Content content) {
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


        private void attachSeries(@NonNull final Content content) {
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

        private void attachPages(@NonNull final Content content, @ViewType int viewType) {
            tvPages.setVisibility(0 == content.getQtyPages() ? View.INVISIBLE : View.VISIBLE);
            Context context = tvPages.getContext();

            String template;
            if (viewType == ViewType.QUEUE || viewType == ViewType.ERRORS) {
                template = context.getResources().getString(R.string.work_pages_queue);
                template = template.replace("@pages@", content.getQtyPages() + "");
                if (viewType == ViewType.ERRORS) {
                    long nbMissingPages = content.getQtyPages() - content.getNbDownloadedPages();
                    if (nbMissingPages > 0)
                        template = template.replace("@missing@", " (" + nbMissingPages + " missing)");
                    else
                        template = template.replace("@missing@", "");
                } else
                    template = template.replace("@missing@", "");
            } else { // Library
                template = context.getResources().getString(R.string.work_pages_library, content.getNbDownloadedPages(), content.getSize() * 1.0 / (1024 * 1024));
            }

            tvPages.setText(template);
        }

        private void attachTags(@NonNull final Content content) {
            Context context = tvTags.getContext();
            List<Attribute> tagsAttributes = content.getAttributeMap().get(AttributeType.TAG);
            if (tagsAttributes == null) {
                tvTags.setText(context.getResources().getString(R.string.work_untitled));
                tvTags.setVisibility(View.GONE);
            } else {
                tvTags.setVisibility(View.VISIBLE);
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

        private void attachButtons(@NonNull final ContentItem item) {
            Content content = item.getContent();
            if (null == content) return;

            // Source icon
            if (content.getSite() != null) {
                int img = content.getSite().getIco();
                ivSite.setImageResource(img);
            } else {
                ivSite.setImageResource(R.drawable.ic_hentoid_shape);
            }

            if (ViewType.QUEUE == item.viewType) {
                boolean isFirstItem = (0 == getAdapterPosition());
                ivTop.setVisibility((isFirstItem) ? View.INVISIBLE : View.VISIBLE);
                ivTop.setVisibility(View.VISIBLE);
                ivBottom.setVisibility(View.VISIBLE);
                ivReorder.setVisibility(View.VISIBLE);
            } else if (ViewType.ERRORS == item.viewType) {
                ivRedownload.setVisibility(View.VISIBLE);
                ivError.setVisibility(View.VISIBLE);
            } else if (ViewType.LIBRARY == item.viewType) {
                if (content.isFavourite()) {
                    ivFavourite.setImageResource(R.drawable.ic_fav_full);
                } else {
                    ivFavourite.setImageResource(R.drawable.ic_fav_empty);
                }
            }
        }

        public static void updateProgress(@NonNull final Content content, @NonNull View rootCardView, int position, boolean isPausedEvent) {
            boolean isQueueReady = ContentQueueManager.getInstance().isQueueActive() && !ContentQueueManager.getInstance().isQueuePaused() && !isPausedEvent;
            boolean isFirstItem = (0 == position);
            ProgressBar pb = rootCardView.findViewById(R.id.pbDownload);

            content.computeProgress();
            content.computeDownloadedBytes();

            if ((isFirstItem && isQueueReady) || content.getPercent() > 0) {
                TextView tvPages = rootCardView.findViewById(R.id.tvPages);
                pb.setVisibility(View.VISIBLE);
                if (content.getPercent() > 0) {
                    pb.setIndeterminate(false);
                    pb.setMax(100);
                    pb.setProgress((int) (content.getPercent() * 100));

                    int color;
                    if (isFirstItem && isQueueReady)
                        color = ThemeHelper.getColor(pb.getContext(), R.color.secondary_light);
                    else
                        color = ContextCompat.getColor(pb.getContext(), R.color.medium_gray);
                    // fixes <= Lollipop progressBar tinting
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                        pb.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
                    else
                        pb.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.MULTIPLY);

                    if (content.getBookSizeEstimate() > 0 && tvPages != null && View.VISIBLE == tvPages.getVisibility()) {
                        String pagesText = tvPages.getText().toString();
                        int separator = pagesText.indexOf(";");
                        if (separator > -1) pagesText = pagesText.substring(0, separator);
                        pagesText = pagesText + String.format(Locale.US, "; estimated %.1f MB", content.getBookSizeEstimate() / (1024 * 1024));
                        tvPages.setText(pagesText);
                    }
                } else {
                    if (isFirstItem && isQueueReady) {
                        pb.setIndeterminate(true);
                        // fixes <= Lollipop progressBar tinting
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                            pb.getIndeterminateDrawable().setColorFilter(ThemeHelper.getColor(pb.getContext(), R.color.secondary_light), PorterDuff.Mode.SRC_IN);
                    } else pb.setVisibility(View.GONE);
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

        public View getBottomButton() {
            return ivBottom;
        }

        public View getDownloadButton() {
            return ivRedownload;
        }


        @Override
        public void unbindView(@NotNull ContentItem item) {
//            item.setUndoSwipeAction(null);
        }

        @Override
        public void onDragged() {
            // TODO fix incorrect visual behaviour when dragging an item to 1st position
            //bookCard.setBackgroundColor(ThemeHelper.getColor(bookCard.getContext(), R.color.white_opacity_25));
        }

        @Override
        public void onDropped() {
            // TODO fix incorrect visual behaviour when dragging an item to 1st position
            //bookCard.setBackground(bookCard.getContext().getDrawable(R.drawable.bg_book_card));
        }
    }
}
