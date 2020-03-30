package me.devsaki.hentoid.adapters;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.github.penfeizhou.animation.apng.APNGDrawable;
import com.github.penfeizhou.animation.io.FilterReader;
import com.github.penfeizhou.animation.io.Reader;
import com.github.penfeizhou.animation.io.StreamReader;
import com.github.penfeizhou.animation.loader.Loader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView;
import me.devsaki.hentoid.customssiv.ImageSource;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;


public final class ImagePagerAdapter extends ListAdapter<ImageFile, ImagePagerAdapter.ImageViewHolder> {

    private static final int IMG_TYPE_OTHER = 0;    // PNGs and JPEGs -> use CustomSubsamplingScaleImageView
    private static final int IMG_TYPE_GIF = 1;      // Static and animated GIFs -> use native Glide
    private static final int IMG_TYPE_APNG = 2;     // Animated PNGs -> use APNG4Android library

    @IntDef({ViewType.IMAGEVIEW, ViewType.IMAGEVIEW_STRETCH, ViewType.SSIV_HORIZONTAL, ViewType.SSIV_VERTICAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {
        int IMAGEVIEW = 0;
        int IMAGEVIEW_STRETCH = 1;
        int SSIV_HORIZONTAL = 2;
        int SSIV_VERTICAL = 3;
    }

    private static final int PX_600_DP = Helper.dpToPixel(HentoidApp.getInstance(), 600);

    private final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    private View.OnTouchListener itemTouchListener;
    private RecyclerView recyclerView;

    // To preload images before they appear on screen with CustomSubsamplingScaleImageView
    private int maxBitmapWidth = -1;
    private int maxBitmapHeight = -1;

    private boolean isScrollLTR = true;

    // Cached prefs
    private int separatingBarsHeight;
    private int viewerOrientation;
    private int displayMode;
    private boolean longTapZoomEnabled;
    private boolean autoRotate;


    public ImagePagerAdapter() {
        super(DIFF_CALLBACK);
        refreshPrefs();
    }

    public void refreshPrefs() {
        int separatingBarsPrefs = Preferences.getViewerSeparatingBars();
        switch (separatingBarsPrefs) {
            case Preferences.Constant.PREF_VIEWER_SEPARATING_BARS_SMALL:
                separatingBarsHeight = 4;
                break;
            case Preferences.Constant.PREF_VIEWER_SEPARATING_BARS_MEDIUM:
                separatingBarsHeight = 16;
                break;
            case Preferences.Constant.PREF_VIEWER_SEPARATING_BARS_LARGE:
                separatingBarsHeight = 64;
                break;
            default:
                separatingBarsHeight = 0;
        }
        viewerOrientation = Preferences.getViewerOrientation();
        longTapZoomEnabled = Preferences.isViewerHoldToZoom();
        displayMode = Preferences.getViewerDisplayMode();
        autoRotate = Preferences.isViewerAutoRotate();
    }

    public void setRecyclerView(RecyclerView v) {
        recyclerView = v;
    }

    public void setItemTouchListener(View.OnTouchListener itemTouchListener) {
        this.itemTouchListener = itemTouchListener;
    }

    public boolean isFavouritePresent() {
        for (ImageFile img : getCurrentList())
            if (img.isFavourite()) return true;

        return false;
    }

    private int getImageType(ImageFile img) {
        if (null == img) return IMG_TYPE_OTHER;

        String extension = FileHelper.getExtension(img.getAbsolutePath());

        if ("gif".equalsIgnoreCase(extension) || img.getMimeType().contains("gif")) {
            return IMG_TYPE_GIF;
        }
        if ("apng".equalsIgnoreCase(extension) || img.getMimeType().contains("apng")) {
            return IMG_TYPE_APNG;
        }
        return IMG_TYPE_OTHER;
    }

    @Override
    public @ViewType
    int getItemViewType(int position) {
        int imageType = getImageType(getImageAt(position));

        if (IMG_TYPE_GIF == imageType || IMG_TYPE_APNG == imageType) return ViewType.IMAGEVIEW;
        if (Preferences.Constant.PREF_VIEWER_DISPLAY_STRETCH == displayMode)
            return ViewType.IMAGEVIEW_STRETCH;
        if (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == viewerOrientation)
            return ViewType.SSIV_VERTICAL;
        return ViewType.SSIV_HORIZONTAL;
    }


    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        View view;
        if (ViewType.IMAGEVIEW == viewType) {
            view = inflater.inflate(R.layout.item_viewer_image_simple, viewGroup, false);
        } else if (ViewType.IMAGEVIEW_STRETCH == viewType) {
            view = inflater.inflate(R.layout.item_viewer_image_simple, viewGroup, false);
            ((ImageView) view).setScaleType(ImageView.ScaleType.FIT_XY);
        } else if (ViewType.SSIV_VERTICAL == viewType) {
            view = inflater.inflate(R.layout.item_viewer_image_subsampling, viewGroup, false);
            ((CustomSubsamplingScaleImageView) view).setIgnoreTouchEvents(true);
            ((CustomSubsamplingScaleImageView) view).setDirection(CustomSubsamplingScaleImageView.Direction.VERTICAL);
        } else {
            view = inflater.inflate(R.layout.item_viewer_image_subsampling, viewGroup, false);
        }

        if (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == viewerOrientation)
            view.setMinimumHeight(PX_600_DP);

        return new ImageViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) { // TODO make all that method less ugly
        if (holder.getItemViewType() == ViewType.SSIV_HORIZONTAL) {
            CustomSubsamplingScaleImageView ssView = (CustomSubsamplingScaleImageView) holder.imgView;
            ssView.setPreloadDimensions(recyclerView.getWidth(), recyclerView.getHeight());
            if (!Preferences.isViewerZoomTransitions()) ssView.setDoubleTapZoomDuration(10);
            ssView.setOffsetLeftSide(isScrollLTR);
        }

        int layoutStyle = (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == viewerOrientation) ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        ViewGroup.LayoutParams layoutParams = holder.imgView.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = layoutStyle;
        holder.imgView.setLayoutParams(layoutParams);
        ImageFile img = getImageAt(position);
        if (img != null) holder.setImage(img);
    }

    @Override
    public void onViewRecycled(@NonNull ImageViewHolder holder) {
        // Set the holder back to its original constraints while in vertical mode
        // (not doing this will cause super high memory usage by trying to load _all_ images)
        if (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == viewerOrientation)
            holder.imgView.setMinimumHeight(PX_600_DP);

        // Free the SSIV's resources
        if (ViewType.SSIV_HORIZONTAL == holder.viewType || ViewType.SSIV_VERTICAL == holder.viewType) // SubsamplingScaleImageView
            ((CustomSubsamplingScaleImageView) holder.imgView).recycle();

        super.onViewRecycled(holder);
    }

    @Nullable
    public ImageFile getImageAt(int position) {
        return (position >= 0 && position < getItemCount()) ? getItem(position) : null;
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

    public void setScrollLTR(boolean isScrollLTR) {
        this.isScrollLTR = isScrollLTR;
    }

    private static final DiffUtil.ItemCallback<ImageFile> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ImageFile>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull ImageFile oldAttr, @NonNull ImageFile newAttr) {
                    return oldAttr.getId() == newAttr.getId();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull ImageFile oldAttr, @NonNull ImageFile newAttr) {
                    return oldAttr.getOrder().equals(newAttr.getOrder()) && oldAttr.getStatus().equals(newAttr.getStatus());
                }
            };

    final class ImageViewHolder extends RecyclerView.ViewHolder implements CustomSubsamplingScaleImageView.OnImageEventListener, RequestListener<Drawable> {

        private final @ViewType
        int viewType;
        private final View imgView;

        private ImageFile img;

        private ImageViewHolder(@NonNull View itemView, @ViewType int viewType) {
            super(itemView);
            this.viewType = viewType;
            imgView = itemView;
            if (Preferences.Constant.PREF_VIEWER_ORIENTATION_HORIZONTAL == viewerOrientation)
                imgView.setOnTouchListener(itemTouchListener);
        }

        void setImage(@NonNull ImageFile img) {
            this.img = img;
            int imgType = getImageType(img);

            String uri = img.getAbsolutePath();
            Timber.i(">>>>IMG %s %s", imgType, uri);

            if (ViewType.SSIV_HORIZONTAL == viewType || ViewType.SSIV_VERTICAL == viewType) { // SubsamplingScaleImageView
                CustomSubsamplingScaleImageView ssView = (CustomSubsamplingScaleImageView) imgView;
                ssView.recycle();
                ssView.setMinimumScaleType(getScaleType());
                ssView.setOnImageEventListener(this);
                ssView.setLongTapZoomEnabled(longTapZoomEnabled);
                ssView.setAutoRotate(autoRotate);
                if (maxBitmapWidth > 0) ssView.setMaxTileSize(maxBitmapWidth, maxBitmapHeight);
                ssView.setImage(ImageSource.uri(uri));
            } else { // ImageView
                if (IMG_TYPE_APNG == imgType) {
                    ImageView view = (ImageView) imgView;

                    APNGDrawable apngDrawable = new APNGDrawable(new ImgLoader(uri));
                    apngDrawable.registerAnimationCallback(animationCallback);
                    view.setImageDrawable(apngDrawable);
                } else {
                    ImageView view = (ImageView) imgView;
                    Glide.with(imgView)
                            .load(uri)
                            .apply(glideRequestOptions)
                            .listener(this)
                            .into(view);
                }
            }
        }

        private int getScaleType() {
            if (Preferences.Constant.PREF_VIEWER_DISPLAY_FILL == displayMode) {
                return CustomSubsamplingScaleImageView.ScaleType.SMART_FILL;
            } else {
                return CustomSubsamplingScaleImageView.ScaleType.CENTER_INSIDE;
            }
        }

        void resetScale() {
            if (ViewType.SSIV_HORIZONTAL == viewType || ViewType.SSIV_VERTICAL == viewType) {
                CustomSubsamplingScaleImageView ssView = (CustomSubsamplingScaleImageView) imgView;
                if (ssView.isImageLoaded() && ssView.isReady() && ssView.isLaidOut())
                    ssView.resetScale();
            }
        }

        private void adjustHeight(int imgHeight) {
            int targetHeight = imgHeight + separatingBarsHeight;
            imgView.setMinimumHeight(targetHeight);
        }

        // == SUBSAMPLINGSCALEVIEW CALLBACKS
        @Override
        public void onReady() {
            if (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
                CustomSubsamplingScaleImageView scaleView = (CustomSubsamplingScaleImageView) imgView;
                adjustHeight((int) (scaleView.getScale() * scaleView.getSHeight()));
            }
        }

        @Override
        public void onImageLoaded() {
            // Nothing special
        }

        @Override
        public void onPreviewLoadError(Throwable e) {
            // Nothing special
        }

        @Override
        public void onImageLoadError(Throwable e) {
            Timber.i(">>>>IMG %s reloaded with Glide", img.getAbsolutePath());
            // Manually force mime-type as GIF to fall back to Glide
            img.setMimeType("image/gif");
            // Reload adapter
            notifyItemChanged(getLayoutPosition());
        }

        @Override
        public void onTileLoadError(Throwable e) {
            Timber.w(e, ">> tileLoad error");
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
            if (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == viewerOrientation)
                adjustHeight(resource.getIntrinsicHeight());
            return false;
        }


        // == APNG4Android ANIMATION CALLBACK
        private final Animatable2Compat.AnimationCallback animationCallback = new Animatable2Compat.AnimationCallback() {
            @Override
            public void onAnimationStart(Drawable drawable) {
                if (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == viewerOrientation)
                    adjustHeight(drawable.getIntrinsicHeight());
            }
        };
    }

    /**
     * Custom image loaders for APNG4Android to work with files located in SAF area
     */
    static class ImgLoader implements Loader {

        private String path;

        ImgLoader(String path) {
            this.path = path;
        }

        @Override
        public synchronized Reader obtain() throws IOException {
            DocumentFile file = FileHelper.getDocumentFile(new File(path), false); // Helper to get a DocumentFile out of the given File
            if (null == file || !file.exists()) return null; // Not triggered
            return new ImgReader(file.getUri());
        }
    }

    static class ImgReader extends FilterReader {
        private Uri uri;

        private static InputStream getInputStream(Uri uri) throws IOException {
            return HentoidApp.getInstance().getContentResolver().openInputStream(uri);
        }

        ImgReader(Uri uri) throws IOException {
            super(new StreamReader(getInputStream(uri)));
            this.uri = uri;
        }

        @Override
        public void reset() throws IOException {
            reader.close();
            reader = new StreamReader(getInputStream(uri));
        }
    }

}
