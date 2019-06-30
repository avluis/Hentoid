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
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.ImageFile;

public class ImageFileFlex extends AbstractFlexibleItem<ImageFileFlex.ImageFileViewHolder> {

    private final ImageFile item;
    private static final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    public ImageFileFlex(ImageFile item) {
        this.item = item;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ImageFileFlex) {
            ImageFileFlex inItem = (ImageFileFlex) o;
            return this.item.equals(inItem.item);
        }
        return false;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_viewer_gallery_image;
    }

    @Override
    public ImageFileViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new ImageFileViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, ImageFileViewHolder holder, int position, List<Object> payloads) {
        holder.setContent(item);
    }

    class ImageFileViewHolder extends FlexibleViewHolder {

        private final TextView pageNumberTxt;
        private final ImageView image;
        private final ImageButton bookmarkBtn;

        ImageFileViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            pageNumberTxt = view.findViewById(R.id.viewer_gallery_pagenumber_text);
            image = view.findViewById(R.id.viewer_gallery_image);
            bookmarkBtn = view.findViewById(R.id.viewer_gallery_bookmark_btn);
        }

        void setContent(ImageFile item) {
            pageNumberTxt.setText(String.format("Page %s", item.getOrder()));
            if (item.isBookmarked()) {
                bookmarkBtn.setImageResource(R.drawable.ic_action_bookmark_on);
            } else {
                bookmarkBtn.setImageResource(R.drawable.ic_action_bookmark_off);
            }
            Glide.with(image.getContext())
                    .load(item.getAbsolutePath())
                    .apply(glideRequestOptions)
                    .into(image);
        }
    }
}
