package me.devsaki.hentoid.viewholders;

import static androidx.core.view.ViewCompat.requireViewById;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.resource.bitmap.CenterInside;
import com.bumptech.glide.request.RequestOptions;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.drag.IExtendedDraggable;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.swipe.ISwipeable;
import com.mikepenz.fastadapter.utils.DragDropUtil;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.GroupItemBundle;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.image.ImageHelper;

public class GroupDisplayItem extends AbstractItem<GroupDisplayItem.GroupViewHolder> implements IExtendedDraggable, ISwipeable {

    private static final RequestOptions glideRequestOptions;

    @IntDef({ViewType.LIBRARY, ViewType.LIBRARY_GRID, ViewType.LIBRARY_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {
        int LIBRARY = 0;
        int LIBRARY_GRID = 1;
        int LIBRARY_EDIT = 2;
    }

    // Group
    private final Group group;
    private final @ViewType
    int viewType;
    private final boolean isEmpty;

    // Drag, drop & swipe
    private final ItemTouchHelper touchHelper;


    static {
        Context context = HentoidApp.getInstance();
        int tintColor = ThemeHelper.getColor(context, R.color.light_gray);

        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_hentoid_trans);
        Drawable d = new BitmapDrawable(context.getResources(), ImageHelper.INSTANCE.tintBitmap(bmp, tintColor));

        final Transformation<Bitmap> centerInside = new CenterInside();
        glideRequestOptions = new RequestOptions()
                .optionalTransform(centerInside)
                .error(d);
    }

    public GroupDisplayItem(Group group, @Nullable ItemTouchHelper touchHelper, @ViewType int viewType) {
        this.group = group;
        this.viewType = viewType;
        this.touchHelper = touchHelper;
        isEmpty = (null == group);
        if (group != null) setIdentifier(group.uniqueHash());
        else setIdentifier(Helper.generateIdForPlaceholder());
    }


    public Group getGroup() {
        return group;
    }

    @NotNull
    @Override
    public GroupViewHolder getViewHolder(@NotNull View view) {
        return new GroupViewHolder(view, viewType);
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public ItemTouchHelper getTouchHelper() {
        return touchHelper;
    }

    @Override
    public int getLayoutRes() {
        return (ViewType.LIBRARY_GRID == viewType) ? R.layout.item_library_group_grid : R.layout.item_library_group;
    }

    @Override
    public int getType() {
        return R.id.group;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public View getDragView(@NotNull RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof GroupViewHolder)
            return ((GroupViewHolder) viewHolder).ivReorder;
        else return null;
    }

    @Override
    public boolean isDraggable() {
        return (ViewType.LIBRARY_EDIT == viewType);
    }

    @Override
    public boolean isSwipeable() {
        return true;
    }

    @Override
    public boolean isDirectionSupported(int i) {
        return (ItemTouchHelper.LEFT == i);
    }


    public static class GroupViewHolder extends FastAdapter.ViewHolder<GroupDisplayItem> {

        private final View baseLayout;
        private final TextView title;
        private final ImageView ivFavourite;
        private final ImageView ivRating;
        private ImageView ivCover;
        private View ivReorder;

        private String coverUri = "";

        GroupViewHolder(View view, @ContentItem.ViewType int viewType) {
            super(view);
            baseLayout = requireViewById(view, R.id.item);
            title = requireViewById(view, R.id.tvTitle);
            ivFavourite = requireViewById(view, R.id.ivFavourite);
            ivRating = requireViewById(view, R.id.iv_rating);

            if (viewType == ViewType.LIBRARY_EDIT) {
                ivReorder = requireViewById(view, R.id.ivReorder);
            } else { // LIBRARY
                ivCover = requireViewById(view, R.id.ivCover);
            }
        }


        @Override
        public void bindView(@NotNull GroupDisplayItem item, @NotNull List<?> payloads) {

            // Payloads are set when the content stays the same but some properties alone change
            if (!payloads.isEmpty()) {
                Bundle bundle = (Bundle) payloads.get(0);
                GroupItemBundle bundleParser = new GroupItemBundle(bundle);

                String stringValue = bundleParser.getCoverUri();
                if (stringValue != null) coverUri = stringValue;

                Boolean boolValue = bundleParser.isFavourite();
                if (boolValue != null) item.group.setFavourite(boolValue);

                Integer intValue = bundleParser.getRating();
                if (intValue != null) item.group.setRating(intValue);
            }

            baseLayout.setVisibility(item.isEmpty ? View.GONE : View.VISIBLE);
            if (item.getGroup() != null && item.getGroup().isBeingProcessed())
                baseLayout.startAnimation(new BlinkAnimation(500, 250));
            else
                baseLayout.clearAnimation();

            if (ivReorder != null) {
                ivReorder.setVisibility(View.VISIBLE);
                DragDropUtil.bindDragHandle(this, item);
            }

            if (ivCover != null) {
                Content coverContent = null;
                if (!item.group.coverContent.isNull())
                    coverContent = item.group.coverContent.getTarget();
                else if (!item.group.items.isEmpty()) {
                    if (item.group.items.get(0).content.isResolved()) {
                        Content c = item.group.items.get(0).content.getTarget();
                        if (c != null) coverContent = c;
                    }
                }
                if (coverContent != null) attachCover(coverContent.getCover());
            }
            List<GroupItem> items = item.group.items;
            String numberStr = (null == items || items.isEmpty()) ? ivFavourite.getContext().getString(R.string.empty) : items.size() + "";
            title.setText(String.format("%s (%s)", item.group.name, numberStr));

            if (item.group.isFavourite()) {
                ivFavourite.setImageResource(R.drawable.ic_fav_full);
            } else {
                ivFavourite.setImageResource(R.drawable.ic_fav_empty);
            }

            if (item.group.isFavourite()) {
                ivFavourite.setImageResource(R.drawable.ic_fav_full);
            } else {
                ivFavourite.setImageResource(R.drawable.ic_fav_empty);
            }

            if (ivRating != null)
                ivRating.setImageResource(ContentHelper.getRatingResourceId(item.group.getRating()));
        }

        private void attachCover(@NonNull ImageFile cover) {
            String thumbLocation = coverUri;
            if (thumbLocation.isEmpty()) thumbLocation = cover.getUsableUri();
            if (thumbLocation.isEmpty()) {
                ivCover.setVisibility(View.INVISIBLE);
                return;
            }

            ivCover.setVisibility(View.VISIBLE);
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

        public View getFavouriteButton() {
            return ivFavourite;
        }

        public View getRatingButton() {
            return ivRating;
        }

        @Override
        public void unbindView(@NotNull GroupDisplayItem item) {
            if (ivCover != null && Helper.isValidContextForGlide(ivCover))
                Glide.with(ivCover).clear(ivCover);
        }
    }
}
