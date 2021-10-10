package me.devsaki.hentoid.viewholders;

import static androidx.core.view.ViewCompat.requireViewById;

import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IExpandable;
import com.mikepenz.fastadapter.IParentItem;
import com.mikepenz.fastadapter.ISubItem;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ImageItemBundle;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.Helper;

public class ImageFileItem extends AbstractItem<ImageFileItem.ImageViewHolder> implements IExpandable<ImageFileItem.ImageViewHolder> {

    private final ImageFile image;
    private boolean isCurrent;
    private boolean expanded = false;

    private static final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    public ImageFileItem(@NonNull ImageFile image) {
        this.image = image;
        setIdentifier(image.uniqueHash());
    }

    // Return a copy, not the original instance that has to remain in synch with its visual representation
    public ImageFile getImage() {
        return image;
    }

    public void setCurrent(boolean current) {
        this.isCurrent = current;
    }

    public boolean isFavourite() {
        return image.isFavourite();
    }


    @NotNull
    @Override
    public ImageViewHolder getViewHolder(@NotNull View view) {
        return new ImageViewHolder(view);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_viewer_gallery_image;
    }

    @Override
    public int getType() {
        return R.id.gallery_image;
    }

    @Override
    public boolean isAutoExpanding() {
        return true;
    }

    @Override
    public boolean isExpanded() {
        return expanded;
    }

    @Override
    public void setExpanded(boolean b) {
        expanded = b;
    }

    @NonNull
    @Override
    public List<ISubItem<?>> getSubItems() {
        return Collections.emptyList();
    }

    @Override
    public void setSubItems(@NonNull List<ISubItem<?>> list) {
        // Nothing
    }

    @Nullable
    @Override
    public IParentItem<?> getParent() {
        return null;
    }

    @Override
    public void setParent(@Nullable IParentItem<?> iParentItem) {
        // Nothing
    }


    public static class ImageViewHolder extends FastAdapter.ViewHolder<ImageFileItem> {

        private static final String HEART_SYMBOL = "❤";

        private final TextView pageNumberTxt;
        private final ImageView image;
        private final ImageView checkedIndicator;

        ImageViewHolder(View view) {
            super(view);
            pageNumberTxt = requireViewById(view, R.id.viewer_gallery_pagenumber_text);
            image = requireViewById(view, R.id.viewer_gallery_image);
            checkedIndicator = requireViewById(view, R.id.checked_indicator);
        }


        @Override
        public void bindView(@NotNull ImageFileItem item, @NotNull List<?> payloads) {

            // Payloads are set when the content stays the same but some properties alone change
            if (!payloads.isEmpty()) {
                Bundle bundle = (Bundle) payloads.get(0);
                ImageItemBundle.Parser bundleParser = new ImageItemBundle.Parser(bundle);

                Boolean boolValue = bundleParser.isFavourite();
                if (boolValue != null) item.image.setFavourite(boolValue);
            }

            updateText(item);

            if (item.isSelected()) checkedIndicator.setVisibility(View.VISIBLE);
            else checkedIndicator.setVisibility(View.GONE);

            Glide.with(image)
                    .load(Uri.parse(item.image.getFileUri()))
                    .apply(glideRequestOptions)
                    .into(image);
        }

        private void updateText(@NotNull ImageFileItem item) {
            String currentBegin = item.isCurrent ? ">" : "";
            String currentEnd = item.isCurrent ? "<" : "";
            String isFavourite = item.isFavourite() ? HEART_SYMBOL : "";
            pageNumberTxt.setText(String.format("%sPage %s%s%s", currentBegin, item.image.getOrder(), isFavourite, currentEnd));
            if (item.isCurrent) pageNumberTxt.setTypeface(null, Typeface.BOLD);
        }

        @Override
        public void unbindView(@NotNull ImageFileItem item) {
            if (image != null && Helper.isValidContextForGlide(image))
                Glide.with(image).clear(image);
        }
    }
}
