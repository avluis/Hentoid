package me.devsaki.hentoid.adapters;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

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


public final class ImagePagerAdapter extends ListAdapter<ImageFile, ImagePagerAdapter.ImageViewHolder> {

    private static final int TYPE_OTHER = 0;    // PNGs and JPEGs -> use CustomSubsamplingScaleImageView
    private static final int TYPE_GIF = 1;      // Static and animated GIFs -> use native Glide
    private static final int TYPE_APNG = 2;     // Animated PNGs -> use APNG4Android library

    private static final int PX_600_DP = Helper.dpToPixel(HentoidApp.getInstance(), 600);

    private static final Executor executor = new ImageLoaderThreadExecutor();
    private final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    private View.OnTouchListener itemTouchListener;
    private RecyclerView recyclerView;

    // To preload images before they appear on screen with CustomSubsamplingScaleImageView
    private int maxBitmapWidth = -1;
    private int maxBitmapHeight = -1;

    // Cached prefs
    private int separatingBarsHeight;
    private int viewerOrientation;


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

    @Override
    public int getItemViewType(int position) {
        ImageFile img = getImageAt(position);
        if (null == img) return TYPE_OTHER;
        String extension = FileHelper.getExtension(img.getAbsolutePath());

        if ("gif".equalsIgnoreCase(extension) || img.getMimeType().contains("gif")) {
            return TYPE_GIF;
        }
        if ("apng".equalsIgnoreCase(extension) || img.getMimeType().contains("apng")) {
            return TYPE_APNG;
        }
        return TYPE_OTHER;
    }


    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        View view;
        if (TYPE_GIF == viewType || TYPE_APNG == viewType) {
            view = inflater.inflate(R.layout.item_viewer_image_simple, viewGroup, false);
        } else if (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
            view = inflater.inflate(R.layout.item_viewer_image_subsampling, viewGroup, false);
            ((CustomSubsamplingScaleImageView) view).setIgnoreTouchEvents(true);
        } else {
            view = inflater.inflate(R.layout.item_viewer_image_subsampling, viewGroup, false);
        }
        return new ImageViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) { // TODO make all that method less ugly
        if (Preferences.Constant.PREF_VIEWER_ORIENTATION_HORIZONTAL == viewerOrientation
                && TYPE_OTHER == holder.imgType) {
            CustomSubsamplingScaleImageView ssView = (CustomSubsamplingScaleImageView) holder.imgView;
            ssView.setPreloadDimensions(recyclerView.getWidth(), recyclerView.getHeight());
            if (!Preferences.isViewerZoomTransitions()) ssView.setDoubleTapZoomDuration(10);
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
                Glide.with(imgView)
                        .load(uri)
                        .apply(glideRequestOptions)
                        .listener(this)
                        .into(view);
            } else if (TYPE_APNG == imgType) {
                ImageView view = (ImageView) imgView;

                APNGDrawable apngDrawable = new APNGDrawable(new ImgLoader(uri));
                apngDrawable.registerAnimationCallback(animationCallback);
                view.setImageDrawable(apngDrawable);
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
            if (TYPE_GIF != imgType && TYPE_APNG != imgType) {
                CustomSubsamplingScaleImageView ssView = (CustomSubsamplingScaleImageView) imgView;
                if (ssView.isImageLoaded() && ssView.isReady() && ssView.isLaidOut())
                    ssView.resetScaleAndCenter();
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
        public void onPreviewLoadError(Exception e) {
            // Nothing special
        }

        @Override
        public void onImageLoadError(Exception e) {
            Timber.i(">>>>IMG %s reloaded with Glide", img.getAbsolutePath());
            // Manually force mime-type as GIF to fall back to Glide
            img.setMimeType("image/gif");
            // Reload adapter
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
