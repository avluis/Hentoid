package me.devsaki.hentoid.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.ImageLoaderThreadExecutor;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;


public final class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder> {

    private static final int TYPE_OTHER = 0;
    private static final int TYPE_GIF = 1;

    private static final Executor executor = new ImageLoaderThreadExecutor();
    private final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    private View.OnTouchListener itemTouchListener;
    private RecyclerView recyclerView;

    private List<ImageFile> images = new ArrayList<>();


    @Override
    public int getItemCount() {
        return images.size();
    }

    public void setImages(List<ImageFile> images) {
        this.images = Collections.unmodifiableList(images);
    }

    public void setRecyclerView(RecyclerView v) {
        recyclerView = v;
    }

    public void setItemTouchListener(View.OnTouchListener itemTouchListener) {
        this.itemTouchListener = itemTouchListener;
    }

    public boolean isFavouritePresent() {
        for (ImageFile img : images)
            if (img.isFavourite()) return true;

        return false;
    }

    @Override
    public int getItemViewType(int position) {
        if ("gif".equalsIgnoreCase(FileHelper.getExtension(images.get(position).getAbsolutePath()))) {
            return TYPE_GIF;
        }
        return TYPE_OTHER;
    }


    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        View view;
        if (TYPE_GIF == viewType) {
            view = inflater.inflate(R.layout.item_viewer_image_glide, viewGroup, false);
        } else if (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == Preferences.getViewerOrientation()) {
            view = inflater.inflate(R.layout.item_viewer_image_subsampling_muted, viewGroup, false);
        } else {
            view = inflater.inflate(R.layout.item_viewer_image_subsampling, viewGroup, false);
        }
        return new ImageViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder viewHolder, int pos) {
        viewHolder.setImageUri(images.get(pos).getAbsolutePath());

        int layoutStyle = (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == Preferences.getViewerOrientation()) ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;

        ViewGroup.LayoutParams layoutParams = viewHolder.imgView.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = layoutStyle;
        viewHolder.imgView.setLayoutParams(layoutParams);
    }

    @Nullable
    public ImageFile getImageAt(int position) {
        return (position >= 0 && position < images.size()) ? images.get(position) : null;
    }

    public void resetPosition(int position) {
        if (recyclerView != null) {
            ImageViewHolder holder = (ImageViewHolder) recyclerView.findViewHolderForAdapterPosition(position);
            if (holder != null) holder.resetScale();
        }
    }

    final class ImageViewHolder extends RecyclerView.ViewHolder {

        private final int imgType;
        private final View imgView;

        private ImageViewHolder(@NonNull View itemView, int imageType) {
            super(itemView);
            imgType = imageType;
            imgView = itemView;

            if (TYPE_OTHER == imgType) {
                ((SubsamplingScaleImageView) imgView).setExecutor(executor);
                imgView.setOnTouchListener(itemTouchListener);
            }
        }

        void setImageUri(String uri) {
            Timber.i(">>>>IMG %s %s", imgType, uri);
            if (TYPE_GIF == imgType) {
                ImageView view = (ImageView) imgView;
                Glide.with(imgView.getContext().getApplicationContext())
                        .load(uri)
                        .apply(glideRequestOptions)
                        .into(view);

            } else {
                SubsamplingScaleImageView ssView = (SubsamplingScaleImageView) imgView;
                ssView.recycle();
                ssView.setMinimumScaleType(getScaleType());
                ssView.setImage(ImageSource.uri(uri));
            }
        }

        private int getScaleType() {
            if (Preferences.Constant.PREF_VIEWER_DISPLAY_FILL == Preferences.getViewerResizeMode()) {
                return SubsamplingScaleImageView.SCALE_TYPE_START;
            } else {
                return SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE;
            }
        }

        void resetScale() {
            if (TYPE_GIF != imgType) {
                SubsamplingScaleImageView ssView = (SubsamplingScaleImageView) imgView;
                if (ssView.isImageLoaded() && ssView.isReady() && ssView.isLaidOut())
                    ssView.resetScaleAndCenter();
            }
        }
    }
}
