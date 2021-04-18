package me.devsaki.hentoid.viewholders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.swipe.IDrawerSwipeableViewHolder;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ContentItemBundle;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.LanguageHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.viewmodels.DuplicateViewModel;
import me.devsaki.hentoid.views.CircularProgressView;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static me.devsaki.hentoid.util.ImageHelper.tintBitmap;

public class DuplicateItem extends AbstractItem<DuplicateItem.ContentViewHolder> {

    private static final int ITEM_HORIZONTAL_MARGIN_PX;
    private static final RequestOptions glideRequestOptions;

    @IntDef({ViewType.MAIN, ViewType.DETAILS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {
        int MAIN = 0;
        int DETAILS = 1;
    }

    private final Content content;
    private final @ViewType
    int viewType;
    private final boolean isEmpty;

//    private Consumer<DuplicateItem> deleteAction = null;


    static {
        Context context = HentoidApp.getInstance();

        int screenWidthPx = HentoidApp.getInstance().getResources().getDisplayMetrics().widthPixels - (2 * (int) context.getResources().getDimension(R.dimen.default_cardview_margin));
        int gridHorizontalWidthPx = (int) context.getResources().getDimension(R.dimen.card_grid_width);
        int nbItems = (int) Math.floor(screenWidthPx * 1f / gridHorizontalWidthPx);
        int remainingSpacePx = screenWidthPx % gridHorizontalWidthPx;
        ITEM_HORIZONTAL_MARGIN_PX = remainingSpacePx / (nbItems * 2);

        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_hentoid_trans);
        int tintColor = ThemeHelper.getColor(context, R.color.light_gray);
        Drawable d = new BitmapDrawable(context.getResources(), tintBitmap(bmp, tintColor));

        glideRequestOptions = new RequestOptions()
                .centerInside()
                .error(d);
    }

    // Constructor for empty placeholder
    public DuplicateItem(@ViewType int viewType) {
        isEmpty = true;
        content = null;
        this.viewType = viewType;
        setIdentifier(Helper.generateIdForPlaceholder());
    }

    // Constructor for library and error item
    public DuplicateItem(DuplicateViewModel.DuplicateResult result, @ViewType int viewType) {
        this.viewType = viewType;
        isEmpty = (null == result);
        if (result != null) setIdentifier(result.hash64());
        else setIdentifier(Helper.generateIdForPlaceholder());

        if (result != null) content = result.getReference();
        else content = null;
    }

    @Nullable
    public Content getContent() {
        return content;
    }

    @NotNull
    @Override
    public DuplicateItem.ContentViewHolder getViewHolder(@NotNull View view) {
        return new ContentViewHolder(view, viewType);
    }

    @Override
    public int getLayoutRes() {
        if (ViewType.MAIN == viewType) return R.layout.item_duplicate_main;
        else if (ViewType.DETAILS == viewType) return R.layout.item_duplicate_detail;
        else return R.layout.item_queue;
    }

    @Override
    public int getType() {
        return R.id.duplicate;
    }


    public static class ContentViewHolder extends FastAdapter.ViewHolder<DuplicateItem> implements IDraggableViewHolder, IDrawerSwipeableViewHolder, ISwipeableViewHolder {

        // Common elements
        private final View baseLayout;
        private final TextView tvTitle;
        private final ImageView ivCover;
        private final ImageView ivFlag;
        private final TextView tvArtist;
        private final TextView tvPages;
        private final ImageView ivSite;
        private final ImageView ivError;

        private final View bookCard;
        private final View deleteButton;

        // Specific to library content
        private View ivNew;
        private TextView tvTags;
        private TextView tvSeries;
        private ImageView ivFavourite;
        private ImageView ivExternal;
        private CircularProgressView readingProgress;

//        private Runnable deleteActionRunnable = null;


        ContentViewHolder(View view, @ViewType int viewType) {
            super(view);

            baseLayout = requireViewById(itemView, R.id.item);
            tvTitle = requireViewById(itemView, R.id.tvTitle);
            ivCover = requireViewById(itemView, R.id.ivCover);
            ivFlag = requireViewById(itemView, R.id.ivFlag);
            ivSite = requireViewById(itemView, R.id.queue_site_button);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvPages = itemView.findViewById(R.id.tvPages);
            ivError = itemView.findViewById(R.id.ivError);
            // Swipe elements
            bookCard = itemView.findViewById(R.id.item_card);
            deleteButton = itemView.findViewById(R.id.delete_btn);

            if (viewType == ViewType.MAIN) {
                // TODO
            } else if (viewType == ViewType.DETAILS) {
                // TODO
            }
        }

        @Override
        public void bindView(@NotNull DuplicateItem item, @NotNull List<?> payloads) {
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
                longValue = bundleParser.getReadPagesCount();
                if (longValue != null) item.content.setReadPagesCount(longValue.intValue());
                String stringValue = bundleParser.getCoverUri();
                if (stringValue != null) item.content.getCover().setFileUri(stringValue);
            }

            /*
            if (item.deleteAction != null)
                deleteActionRunnable = () -> item.deleteAction.accept(item);

             */

            updateLayoutVisibility(item);
            attachCover(item.content);
            attachFlag(item.content);
            attachTitle(item.content);
            if (readingProgress != null) attachReadingProgress(item.content);
            if (tvArtist != null) attachArtist(item.content);
            if (tvSeries != null) attachSeries(item.content);
            if (tvPages != null) attachPages(item.content, item.viewType);
            if (tvTags != null) attachTags(item.content);
            attachButtons(item);
        }

        private void updateLayoutVisibility(@NonNull final DuplicateItem item) {
            baseLayout.setVisibility(item.isEmpty ? View.GONE : View.VISIBLE);

            if (Preferences.Constant.LIBRARY_DISPLAY_GRID == Preferences.getLibraryDisplay()) {
                ViewGroup.LayoutParams layoutParams = baseLayout.getLayoutParams();
                if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) layoutParams).setMarginStart(ITEM_HORIZONTAL_MARGIN_PX);
                    ((ViewGroup.MarginLayoutParams) layoutParams).setMarginEnd(ITEM_HORIZONTAL_MARGIN_PX);
                }
                baseLayout.setLayoutParams(layoutParams);
            }

            if (item.getContent() != null && item.getContent().isBeingDeleted())
                baseLayout.startAnimation(new BlinkAnimation(500, 250));
            else
                baseLayout.clearAnimation();

            // Unread indicator
            if (ivNew != null)
                ivNew.setVisibility((0 == item.getContent().getReads()) ? View.VISIBLE : View.GONE);
        }

        private void attachCover(@NonNull final Content content) {
            ImageFile cover = content.getCover();
            String thumbLocation = cover.getUsableUri();
            if (thumbLocation.isEmpty()) {
                ivCover.setVisibility(View.INVISIBLE);
                return;
            }

            ivCover.setVisibility(View.VISIBLE);
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
                    String userAgent = content.getSite().getUserAgent();
                    if (cookiesStr != null) {
                        LazyHeaders.Builder builder = new LazyHeaders.Builder()
                                .addHeader(HttpHelper.HEADER_COOKIE_KEY, cookiesStr)
                                .addHeader(HttpHelper.HEADER_USER_AGENT, userAgent);

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

        private void attachFlag(@NonNull final Content content) {
            List<Attribute> langAttributes = content.getAttributeMap().get(AttributeType.LANGUAGE);
            if (langAttributes != null && !langAttributes.isEmpty())
                for (Attribute lang : langAttributes) {
                    @DrawableRes int resId = LanguageHelper.getFlagFromLanguage(ivFlag.getContext(), lang.getName());
                    if (resId != 0) {
                        ivFlag.setImageResource(resId);
                        ivFlag.setVisibility(View.VISIBLE);
                        return;
                    }
                }
            ivFlag.setVisibility(View.GONE);
        }

        private void attachTitle(@NonNull final Content content) {
            CharSequence title;
            if (content.getTitle() == null) {
                title = tvTitle.getContext().getText(R.string.work_untitled);
            } else {
                title = content.getTitle();
            }
            tvTitle.setText(title);
            tvTitle.setTextColor(ThemeHelper.getColor(tvTitle.getContext(), R.color.card_title_light));
        }

        private void attachReadingProgress(@NonNull final Content content) {
            List<ImageFile> imgs = content.getImageFiles();
            if (imgs != null) {
                readingProgress.setVisibility(View.VISIBLE);
                readingProgress.setTotalColor(readingProgress.getContext(), R.color.transparent);
                readingProgress.setTotal(Stream.of(content.getImageFiles()).withoutNulls().filter(ImageFile::isReadable).count());
                readingProgress.setProgress1(content.getReadPagesCount());
            }
        }

        private void attachArtist(@NonNull final Content content) {
            Context context = tvArtist.getContext();
            List<Attribute> attributes = new ArrayList<>();

            List<Attribute> artistAttributes = content.getAttributeMap().get(AttributeType.ARTIST);
            if (artistAttributes != null)
                attributes.addAll(artistAttributes);
            List<Attribute> circleAttributes = content.getAttributeMap().get(AttributeType.CIRCLE);
            if (circleAttributes != null)
                attributes.addAll(circleAttributes);

            if (attributes.isEmpty()) {
                tvArtist.setText(context.getString(R.string.work_artist, context.getResources().getString(R.string.work_untitled)));
            } else {
                List<String> allArtists = new ArrayList<>();
                for (Attribute attribute : attributes) {
                    allArtists.add(attribute.getName());
                }
                String artists = android.text.TextUtils.join(", ", allArtists);
                tvArtist.setText(context.getString(R.string.work_artist, artists));
            }
        }


        private void attachSeries(@NonNull final Content content) {
            List<Attribute> seriesAttributes = content.getAttributeMap().get(AttributeType.SERIE);
            if (seriesAttributes == null || seriesAttributes.isEmpty()) {
                tvSeries.setVisibility(View.GONE);
            } else {
                tvSeries.setVisibility(View.VISIBLE);
                List<String> allSeries = new ArrayList<>();
                for (Attribute attribute : seriesAttributes) {
                    allSeries.add(attribute.getName());
                }
                String series = android.text.TextUtils.join(", ", allSeries);
                tvSeries.setText(tvSeries.getContext().getString(R.string.work_series, series));
            }
        }

        private void attachPages(@NonNull final Content content, @ViewType int viewType) {
            tvPages.setVisibility(0 == content.getQtyPages() ? View.INVISIBLE : View.VISIBLE);
            Context context = tvPages.getContext();

            String template = context.getResources().getString(R.string.work_pages_library, content.getNbDownloadedPages(), content.getSize() * 1.0 / (1024 * 1024));

            tvPages.setText(template);
        }

        private void attachTags(@NonNull final Content content) {
            String tagTxt = ContentHelper.formatTags(content);
            if (tagTxt.isEmpty()) {
                tvTags.setVisibility(View.GONE);
            } else {
                tvTags.setVisibility(View.VISIBLE);
                tvTags.setText(tagTxt);
                tvTags.setTextColor(ThemeHelper.getColor(tvTags.getContext(), R.color.card_tags_light));
            }
        }

        private void attachButtons(@NonNull final DuplicateItem item) {
            Content content = item.getContent();
            if (null == content) return;

            // Source icon
            Site site = content.getSite();
            if (site != null && !site.equals(Site.NONE)) {
                int img = site.getIco();
                ivSite.setImageResource(img);
                ivSite.setVisibility(View.VISIBLE);
            } else {
                ivSite.setVisibility(View.GONE);
            }

            ivExternal.setVisibility(content.getStatus().equals(StatusContent.EXTERNAL) ? View.VISIBLE : View.GONE);
            if (content.isFavourite()) {
                ivFavourite.setImageResource(R.drawable.ic_fav_full);
            } else {
                ivFavourite.setImageResource(R.drawable.ic_fav_empty);
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


        @Override
        public void unbindView(@NotNull DuplicateItem item) {
//            deleteActionRunnable = null;
            bookCard.setTranslationX(0f);
            if (ivCover != null && Helper.isValidContextForGlide(ivCover))
                Glide.with(ivCover).clear(ivCover);
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

        @NotNull
        @Override
        public View getSwipeableView() {
            return bookCard;
        }

        @Override
        public void onSwiped() {
            if (deleteButton != null) deleteButton.setVisibility(View.VISIBLE);
        }

        @Override
        public void onUnswiped() {
            if (deleteButton != null) deleteButton.setVisibility(View.GONE);
        }
    }
}
