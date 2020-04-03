package me.devsaki.hentoid.viewholders;

import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.ImageFile;

import static androidx.core.view.ViewCompat.requireViewById;

public class ImageFileItem extends AbstractItem<ImageFileItem.ImageViewHolder> {

    private final ImageFile image;
    private boolean isCurrent;
    private static final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    public ImageFileItem(@NonNull ImageFile image) {
        this.image = image;
        setIdentifier(image.getId());
    }

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


    public static class ImageViewHolder extends FastAdapter.ViewHolder<ImageFileItem> {

        private final TextView pageNumberTxt;
        private final ImageView image;
        private final ImageButton favouriteBtn;

        ImageViewHolder(View view) {
            super(view);
            pageNumberTxt = requireViewById(view, R.id.viewer_gallery_pagenumber_text);
            image = requireViewById(view, R.id.viewer_gallery_image);
            favouriteBtn = requireViewById(view, R.id.viewer_gallery_favourite_btn);
        }


        @Override
        public void bindView(@NotNull ImageFileItem item, @NotNull List<?> list) {
            String currentBegin = item.isCurrent ? ">" : "";
            String currentEnd = item.isCurrent ? "<" : "";
            pageNumberTxt.setText(String.format("%sPage %s%s", currentBegin, item.image.getOrder(), currentEnd));
            if (item.isCurrent) pageNumberTxt.setTypeface(null, Typeface.BOLD);
            updateFavourite(item.isFavourite());
            Glide.with(image.getContext().getApplicationContext())
                    .load(item.image.getAbsolutePath())
                    .apply(glideRequestOptions)
                    .into(image);
        }

        void updateFavourite(boolean isFavourite) {
            if (isFavourite) {
                favouriteBtn.setImageResource(R.drawable.ic_fav_full);
            } else {
                favouriteBtn.setImageResource(R.drawable.ic_fav_empty);
            }
        }

        public View getFavouriteButton() {
            return favouriteBtn;
        }

        @Override
        public void unbindView(@NotNull ImageFileItem item) {
            // No specific behaviour to implement
        }
    }
}
