package me.devsaki.hentoid.viewholders;

import static androidx.core.view.ViewCompat.requireViewById;

import android.content.Context;
import android.content.res.Resources;
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
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.resource.bitmap.CenterInside;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.DuplicateItemBundle;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.DuplicateEntry;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.image.ImageHelperK;

public class DuplicateItem extends AbstractItem<DuplicateItem.ContentViewHolder> {

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
    private final boolean isReferenceItem;
    private final boolean canDelete;

    private int nbDuplicates = 0;
    private Float titleScore = -1f;
    private Float coverScore = -1f;
    private Float artistScore = -1f;
    private Float totalScore = -1f;
    private boolean keep = true;
    private boolean isBeingDeleted = false;

    static {
        Context context = HentoidApp.getInstance();

        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_hentoid_trans);
        int tintColor = ThemeHelper.getColor(context, R.color.light_gray);
        Drawable d = new BitmapDrawable(context.getResources(), ImageHelperK.INSTANCE.tintBitmap(bmp, tintColor));

        final Transformation<Bitmap> centerInside = new CenterInside();
        glideRequestOptions = new RequestOptions()
                .optionalTransform(centerInside)
                .error(d);
    }

    public DuplicateItem(DuplicateEntry result, @ViewType int viewType) {
        this.viewType = viewType;
        isEmpty = (null == result);

        if (result != null) {
            setIdentifier(result.uniqueHash());
            if (viewType == ViewType.MAIN) {
                content = result.getReferenceContent();
            } else {
                content = result.getDuplicateContent();
                titleScore = result.getTitleScore();
                coverScore = result.getCoverScore();
                artistScore = result.getArtistScore();
                totalScore = result.calcTotalScore();
                keep = result.getKeep();
                isBeingDeleted = result.isBeingDeleted();
            }
            canDelete = (content != null && (!content.getStatus().equals(StatusContent.EXTERNAL) || Preferences.isDeleteExternalLibrary()));
            nbDuplicates = result.getNbDuplicates();
        } else {
            setIdentifier(Helper.generateIdForPlaceholder());
            content = null;
            canDelete = false;
        }
        isReferenceItem = (titleScore > 1f);
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

    public boolean getKeep() {
        return keep;
    }

    public boolean isBeingDeleted() {
        return isBeingDeleted;
    }

    public static class ContentViewHolder extends FastAdapter.ViewHolder<DuplicateItem> {

        // Common elements
        private final View baseLayout;
        private final TextView tvTitle;
        private final ImageView ivCover;
        private final ImageView ivFlag;
        private final TextView tvArtist;
        private final TextView tvPages;
        private final ImageView ivSite;
        private final ImageView ivFavourite;
        private final ImageView ivExternal;
        // Specific to main screen
        private TextView viewDetails;
        // Specific to details screen
        private TextView tvLaunchCode;
        private Group scores;
        private Group keepDeleteGroup;
        private TextView titleScore;
        private TextView coverScore;
        private TextView artistScore;
        private TextView totalScore;
        private TextView keepButton;
        private TextView deleteButton;
        private MaterialSwitch keepDeleteSwitch;


        ContentViewHolder(View view, @ViewType int viewType) {
            super(view);

            baseLayout = requireViewById(itemView, R.id.item);
            tvTitle = requireViewById(itemView, R.id.tvTitle);
            ivCover = requireViewById(itemView, R.id.ivCover);
            ivFlag = requireViewById(itemView, R.id.ivFlag);
            ivSite = requireViewById(itemView, R.id.ivSite);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvPages = itemView.findViewById(R.id.tvPages);
            ivFavourite = itemView.findViewById(R.id.ivFavourite);
            ivExternal = itemView.findViewById(R.id.ivExternal);

            if (viewType == ViewType.MAIN) {
                viewDetails = itemView.findViewById(R.id.view_details);
            } else if (viewType == ViewType.DETAILS) {
                tvLaunchCode = itemView.findViewById(R.id.tvLaunchCode);
                scores = itemView.findViewById(R.id.scores);
                keepDeleteGroup = itemView.findViewById(R.id.keep_delete_group);
                titleScore = itemView.findViewById(R.id.title_score);
                coverScore = itemView.findViewById(R.id.cover_score);
                artistScore = itemView.findViewById(R.id.artist_score);
                totalScore = itemView.findViewById(R.id.total_score);
                keepButton = itemView.findViewById(R.id.keep_btn);
                deleteButton = itemView.findViewById(R.id.delete_btn);
                keepDeleteSwitch = itemView.findViewById(R.id.keep_delete);
            }
        }

        @Override
        public void bindView(@NotNull DuplicateItem item, @NotNull List<?> payloads) {
            if (item.isEmpty || null == item.content) return; // Ignore placeholders from PagedList

            // Payloads are set when the content stays the same but some properties alone change
            if (!payloads.isEmpty()) {
                Bundle bundle = (Bundle) payloads.get(0);
                DuplicateItemBundle bundleParser = new DuplicateItemBundle(bundle);

                Boolean boolValue = bundleParser.isKeep();
                if (boolValue != null) item.keep = boolValue;
                boolValue = bundleParser.isBeingDeleted();
                if (boolValue != null) item.isBeingDeleted = boolValue;
            }

            updateLayoutVisibility(item);
            attachCover(item.content);
            attachFlag(item.content);
            attachTitle(item.content);
            if (tvLaunchCode != null) attachLaunchCode(item.content);
            if (tvArtist != null) attachArtist(item.content);
            if (tvPages != null) attachPages(item.content);
            if (titleScore != null) attachScores(item);
            attachButtons(item);
        }

        private void updateLayoutVisibility(@NonNull final DuplicateItem item) {
            baseLayout.setVisibility(item.isEmpty ? View.GONE : View.VISIBLE);

            if (item.isBeingDeleted)
                baseLayout.startAnimation(new BlinkAnimation(500, 250));
            else
                baseLayout.clearAnimation();
        }

        private void attachCover(@NonNull final Content content) {
            String thumbLocation = content.getCover().getUsableUri();
            if (thumbLocation.isEmpty()) {
                ivCover.setVisibility(View.INVISIBLE);
                return;
            }

            ivCover.setVisibility(View.VISIBLE);
            // Use content's cookies to load image (useful for ExHentai when viewing queue screen)
            if (thumbLocation.startsWith("http")) {
                GlideUrl glideUrl = ContentHelper.bindOnlineCover(content, thumbLocation);
                if (glideUrl != null) {
                    Glide.with(ivCover)
                            .load(glideUrl)
                            .apply(glideRequestOptions)
                            .into(ivCover);
                }
            } else
                Glide.with(ivCover)
                        .load(Uri.parse(thumbLocation))
                        .apply(glideRequestOptions)
                        .into(ivCover);
        }

        private void attachFlag(@NonNull final Content content) {
            @DrawableRes int resId = ContentHelper.getFlagResourceId(ivFlag.getContext(), content);
            if (resId != 0) {
                ivFlag.setImageResource(resId);
                ivFlag.setVisibility(View.VISIBLE);
            } else {
                ivFlag.setVisibility(View.GONE);
            }
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

        private void attachLaunchCode(@NonNull final Content content) {
            Resources res = tvPages.getContext().getResources();
            tvLaunchCode.setText(res.getString(R.string.book_launchcode, content.getUniqueSiteId()));
        }

        private void attachArtist(@NonNull final Content content) {
            tvArtist.setText(ContentHelper.formatArtistForDisplay(tvArtist.getContext(), content));
        }

        private void attachPages(@NonNull final Content content) {
            tvPages.setVisibility(0 == content.getQtyPages() ? View.INVISIBLE : View.VISIBLE);
            Context context = tvPages.getContext();

            String template = context.getResources().getQuantityString(R.plurals.work_pages_library, (int) content.getNbDownloadedPages(), content.getNbDownloadedPages(), content.getSize() * 1.0 / (1024 * 1024));

            tvPages.setText(template);
        }

        private void attachScores(@NonNull final DuplicateItem item) {
            Resources res = titleScore.getContext().getResources();

            if (!item.isReferenceItem) {
                scores.setVisibility(View.VISIBLE);

                if (item.titleScore > -1.0)
                    titleScore.setText(res.getString(R.string.duplicate_title_score, item.titleScore * 100));
                else titleScore.setText(R.string.duplicate_title_score_nodata);

                if (item.coverScore > -1.0)
                    coverScore.setText(res.getString(R.string.duplicate_cover_score, item.coverScore * 100));
                else coverScore.setText(R.string.duplicate_cover_score_nodata);

                if (item.artistScore > -1.0)
                    artistScore.setText(res.getString(R.string.duplicate_artist_score, item.artistScore * 100));
                else artistScore.setText(R.string.duplicate_artist_score_nodata);

                totalScore.setText(res.getString(R.string.duplicate_total_score, item.totalScore * 100));
            } else { // Reference item
                scores.setVisibility(View.GONE);
            }
        }

        private void attachButtons(@NonNull final DuplicateItem item) {
            Context context = tvPages.getContext();
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

            // External icon
            if (content.getStatus().equals(StatusContent.EXTERNAL)) {
                if (content.isArchive())
                    ivExternal.setImageResource(R.drawable.ic_archive);
                else
                    ivExternal.setImageResource(R.drawable.ic_folder_full);
                ivExternal.setVisibility(View.VISIBLE);
            } else
                ivExternal.setVisibility(View.GONE);

            // Favourite icon
            if (content.isFavourite()) {
                ivFavourite.setImageResource(R.drawable.ic_fav_full);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ivFavourite.setTooltipText(context.getText(R.string.book_favourite_success));
            } else {
                ivFavourite.setImageResource(R.drawable.ic_fav_empty);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ivFavourite.setTooltipText(context.getText(R.string.book_unfavourite_success));
            }

            // View details icon
            if (viewDetails != null)
                viewDetails.setText(context.getResources().getQuantityString(R.plurals.duplicate_count, item.nbDuplicates + 1, item.nbDuplicates + 1));

            if (item.canDelete) {
                if (keepDeleteGroup != null) keepDeleteGroup.setVisibility(View.VISIBLE);

                // Keep and delete buttons
                if (keepButton != null) {
                    @ColorInt int targetColor = (item.keep) ?
                            ThemeHelper.getColor(context, R.color.secondary_light) :
                            ContextCompat.getColor(context, R.color.medium_gray);
                    keepButton.setTextColor(targetColor);
                    Drawable[] drawables = keepButton.getCompoundDrawablesRelative();
                    if (drawables[0] != null) {
                        drawables[0].setColorFilter(targetColor, PorterDuff.Mode.SRC_IN);
                    }
                    keepButton.setOnClickListener(v -> {
                        keepDeleteSwitch.setChecked(true);
                        keepDeleteSwitch.callOnClick();
                    });
                }
                if (deleteButton != null) {
                    @ColorInt int targetColor = (!item.keep) ?
                            ThemeHelper.getColor(context, R.color.secondary_light) :
                            ContextCompat.getColor(context, R.color.medium_gray);
                    deleteButton.setTextColor(targetColor);
                    Drawable[] drawables = deleteButton.getCompoundDrawablesRelative();
                    if (drawables[0] != null) {
                        drawables[0].setColorFilter(targetColor, PorterDuff.Mode.SRC_IN);
                    }
                    deleteButton.setOnClickListener(v -> {
                        keepDeleteSwitch.setChecked(false);
                        keepDeleteSwitch.callOnClick();
                    });
                }
                if (keepDeleteSwitch != null) keepDeleteSwitch.setChecked(item.keep);
            } else {
                if (keepDeleteGroup != null) keepDeleteGroup.setVisibility(View.INVISIBLE);
                if (keepDeleteSwitch != null) keepDeleteSwitch.setChecked(true);
            }
        }

        public MaterialSwitch getKeepDeleteSwitch() {
            return keepDeleteSwitch;
        }

        @Override
        public void unbindView(@NotNull DuplicateItem item) {
            if (ivCover != null && Helper.isValidContextForGlide(ivCover))
                Glide.with(ivCover).clear(ivCover);
        }
    }
}
