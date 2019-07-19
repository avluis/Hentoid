package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.adapters.ImageGalleryAdapter;
import me.devsaki.hentoid.database.domains.ImageFile;

public class ImageFileFlex extends AbstractFlexibleItem<ImageFileFlex.ImageFileViewHolder> implements IFilterable<Boolean> {

    private final ImageFile item;
    private static final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    public ImageFileFlex(ImageFile item) {
        this.item = item;
    }

    public ImageFile getItem() {
        return item;
    }


    @Override
    public boolean equals(Object o) {
        if (o instanceof ImageFileFlex) {
            ImageFileFlex inItem = (ImageFileFlex) o;
            return this.item.equals(inItem.item);
        }
        return false;
    }


    public int hashCode() {
        return item.hashCode();
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_viewer_gallery_image;
    }

    @Override
    public ImageFileViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new ImageFileViewHolder(view, (ImageGalleryAdapter) adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, ImageFileViewHolder holder, int position, List<Object> payloads) {
        holder.setContent(item);
    }

    @Override
    public boolean filter(Boolean constraint) {
        return (!constraint || item.isFavourite());
    }

    public boolean isFavourite() {
        return item.isFavourite();
    }

    class ImageFileViewHolder extends FlexibleViewHolder {

        private final TextView pageNumberTxt;
        private final ImageView image;
        private final ImageButton favouriteBtn;
        private ImageFile imageFile;

        ImageFileViewHolder(View view, ImageGalleryAdapter adapter) {
            super(view, adapter);
            pageNumberTxt = view.findViewById(R.id.viewer_gallery_pagenumber_text);
            image = view.findViewById(R.id.viewer_gallery_image);
            favouriteBtn = view.findViewById(R.id.viewer_gallery_favourite_btn);
            favouriteBtn.setOnClickListener(v -> onFavouriteClicked());
        }

        void setContent(ImageFile item) {
            imageFile = item;
            pageNumberTxt.setText(String.format("Page %s", item.getOrder()));
            updateFavourite(item.isFavourite());
            Glide.with(image.getContext().getApplicationContext())
                    .load(item.getAbsolutePath())
                    .apply(glideRequestOptions)
                    .into(image);
        }

        void onFavouriteClicked() {
            ((ImageGalleryAdapter) mAdapter).getOnFavouriteClickListener().accept(imageFile);
        }

        void updateFavourite(boolean isFavourite) {
            if (isFavourite) {
                favouriteBtn.setImageResource(R.drawable.ic_fav_full);
            } else {
                favouriteBtn.setImageResource(R.drawable.ic_fav_empty);
            }
        }
    }
}
