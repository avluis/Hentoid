package me.devsaki.hentoid.adapters;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ImageLoaderThreadExecutor;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.views.ssiv.CustomSubsamplingScaleImageView;
import me.devsaki.hentoid.views.ssiv.ImageSource;
import timber.log.Timber;


public final class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder> {

    private static final int TYPE_OTHER = 0;
    private static final int TYPE_GIF = 1;

    private static final int PX_600_DP = Helper.dpToPixel(HentoidApp.getInstance(), 600);

    private static final Executor executor = new ImageLoaderThreadExecutor();
    private final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    private View.OnTouchListener itemTouchListener;
    private RecyclerView recyclerView;

    private List<ImageFile> images = new ArrayList<>();

    // To preload images before they appear on screen with SubsamplingScaleImageView
    private int maxBitmapWidth = -1;
    private int maxBitmapHeight = -1;


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
        ImageFile img = images.get(position);

        if ("gif".equalsIgnoreCase(FileHelper.getExtension(img.getAbsolutePath()))
                || img.getMimeType().contains("gif")) {
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
            view = inflater.inflate(R.layout.item_viewer_image_subsampling, viewGroup, false);
            ((CustomSubsamplingScaleImageView) view).setIgnoreTouchEvents(true);
        } else {
            view = inflater.inflate(R.layout.item_viewer_image_subsampling, viewGroup, false);
        }
        return new ImageViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder viewHolder, int pos) { // TODO make all that method less ugly
        if (Preferences.Constant.PREF_VIEWER_ORIENTATION_HORIZONTAL == Preferences.getViewerOrientation()
                && TYPE_OTHER == viewHolder.imgType) {
            CustomSubsamplingScaleImageView ssView = (CustomSubsamplingScaleImageView) viewHolder.imgView;
            ssView.setPreloadDimensions(recyclerView.getWidth(), recyclerView.getHeight());
            if (!Preferences.isViewerZoomTransitions()) ssView.setDoubleTapZoomDuration(10);
        }

        int layoutStyle = (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == Preferences.getViewerOrientation()) ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        ViewGroup.LayoutParams layoutParams = viewHolder.imgView.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = layoutStyle;
        viewHolder.imgView.setLayoutParams(layoutParams);
        viewHolder.setImage(images.get(pos));
    }


    @Override
    public void onViewRecycled(@NonNull ImageViewHolder holder) {
        // Set the holder back to its original constraints while in vertical mode
        // (not doing this will cause super high memory usage by trying to load _all_ images)
        if (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == Preferences.getViewerOrientation())
            holder.imgView.setMinimumHeight(PX_600_DP);

        super.onViewRecycled(holder);
    }

    @Nullable
    public ImageFile getImageAt(int position) {
        return (position >= 0 && position < images.size()) ? images.get(position) : null;
    }

    public void resetScaleAtPosition(int position) {
        if (recyclerView != null) {
            ImageViewHolder holder = (ImageViewHolder) recyclerView.findViewHolderForAdapterPosition(position);
            if (holder != null) holder.resetScale();
        }
    }

    public void setMaxDimensions(int maxWidth, int maxHeight) {
        maxBitmapWidth = maxWidth;
        maxBitmapHeight = maxHeight;
    }

    final class ImageViewHolder extends RecyclerView.ViewHolder implements CustomSubsamplingScaleImageView.OnImageEventListener, RequestListener<Drawable> {

        private final int imgType;
        private final View imgView;
        private ImageFile img;

        private ImageViewHolder(@NonNull View itemView, int imageType) {
            super(itemView);
            imgType = imageType;
            imgView = itemView;

            if (TYPE_OTHER == imgType) {
                ((CustomSubsamplingScaleImageView) imgView).setExecutor(executor);
                imgView.setOnTouchListener(itemTouchListener);
            }
        }

        void setImage(ImageFile img) {
            this.img = img;
            String uri = img.getAbsolutePath();
            Timber.i(">>>>IMG %s %s", imgType, uri);

            if (TYPE_GIF == imgType) {
                ImageView view = (ImageView) imgView;
                Glide.with(imgView.getContext().getApplicationContext())
                        .load(uri)
                        .apply(glideRequestOptions)
                        .listener(this)
                        .into(view);

            } else {
                CustomSubsamplingScaleImageView ssView = (CustomSubsamplingScaleImageView) imgView;
                ssView.recycle();
                ssView.setMinimumScaleType(getScaleType());
                ssView.setOnImageEventListener(this);
                if (maxBitmapWidth > 0) ssView.setMaxTileSize(maxBitmapWidth, maxBitmapHeight);
                ssView.setImage(ImageSource.uri(uri));
            }
        }

        private int getScaleType() {
            if (Preferences.Constant.PREF_VIEWER_DISPLAY_FILL == Preferences.getViewerResizeMode()) {
                return CustomSubsamplingScaleImageView.SCALE_TYPE_START;
            } else {
                return CustomSubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE;
            }
        }

        void resetScale() {
            if (TYPE_GIF != imgType) {
                CustomSubsamplingScaleImageView ssView = (CustomSubsamplingScaleImageView) imgView;
                if (ssView.isImageLoaded() && ssView.isReady() && ssView.isLaidOut())
                    ssView.resetScaleAndCenter();
            }
        }

        private void adjustHeight() {
            imgView.setMinimumHeight(0); // TODO - use this to create separation bars in vertical mode (#419)
        }

        // == SUBSAMPLINGSCALEVIEW CALLBACKS
        @Override
        public void onReady() {
            adjustHeight();
        }

        @Override
        public void onImageLoaded() {
            // Nothing special
        }

        @Override
        public void onPreviewLoadError(Exception e) {
            // Nothing special
        }

        @Override
        public void onImageLoadError(Exception e) {
            // Mark image as GIF
            img.setMimeType("image/gif");
            // Reload to fall back to Glide
            notifyItemChanged(getLayoutPosition());
        }

        @Override
        public void onTileLoadError(Exception e) {
            // Nothing special
        }

        @Override
        public void onPreviewReleased() {
            // Nothing special
        }


        // == GLIDE CALLBACKS
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            adjustHeight();
            return false;
        }
    }
}
