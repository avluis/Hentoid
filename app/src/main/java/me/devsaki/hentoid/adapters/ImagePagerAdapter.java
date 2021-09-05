package me.devsaki.hentoid.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.renderscript.RenderScript;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Objects;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView;
import me.devsaki.hentoid.customssiv.ImageSource;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;


public final class ImagePagerAdapter extends ListAdapter<ImageFile, ImagePagerAdapter.ImageViewHolder> {

    private static final int IMG_TYPE_OTHER = 0;    // PNGs and JPEGs -> use CustomSubsamplingScaleImageView
    private static final int IMG_TYPE_GIF = 1;      // Static and animated GIFs -> use native Glide
    private static final int IMG_TYPE_APNG = 2;     // Animated PNGs -> use APNG4Android library

    @IntDef({ViewType.DEFAULT, ViewType.IMAGEVIEW_STRETCH, ViewType.SSIV_VERTICAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {
        int DEFAULT = 0;
        int IMAGEVIEW_STRETCH = 1;
        int SSIV_VERTICAL = 2;
    }

    // Screen width and height; used to adjust dimensions of small images handled by Glide
    static final int SCREEN_WIDTH = HentoidApp.getInstance().getResources().getDisplayMetrics().widthPixels;
    static final int SCREEN_HEIGHT = HentoidApp.getInstance().getResources().getDisplayMetrics().heightPixels;

    private static final int PAGE_MIN_HEIGHT = (int) HentoidApp.getInstance().getResources().getDimension(R.dimen.page_min_height);


    private final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    private View.OnTouchListener itemTouchListener;
    private RecyclerView recyclerView;

    // To preload images before they appear on screen with CustomSubsamplingScaleImageView
    private int maxBitmapWidth = -1;
    private int maxBitmapHeight = -1;

    private boolean isScrollLTR = true;

    // Single instance of RenderScript
    private RenderScript rs;

    // Cached prefs
    private int separatingBarsHeight;
    private int viewerOrientation;
    private int displayMode;
    private boolean longTapZoomEnabled;
    private boolean autoRotate;
    private boolean isSmoothRendering;
    private float doubleTapZoomCap;

    public ImagePagerAdapter(Context context) {
        super(IMAGE_DIFF_CALLBACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) rs = RenderScript.create(context);
    }

    // Book prefs have to be set explicitely because the cached Content linked from each ImageFile
    // might not have the latest properties
    public void refreshPrefs(@NonNull final Map<String, String> bookPreferences) {
        int separatingBarsPrefs = Preferences.getViewerSeparatingBars();
        switch (separatingBarsPrefs) {
            case Preferences.Constant.VIEWER_SEPARATING_BARS_SMALL:
                separatingBarsHeight = 4;
                break;
            case Preferences.Constant.VIEWER_SEPARATING_BARS_MEDIUM:
                separatingBarsHeight = 16;
                break;
            case Preferences.Constant.VIEWER_SEPARATING_BARS_LARGE:
                separatingBarsHeight = 64;
                break;
            default:
                separatingBarsHeight = 0;
        }
        longTapZoomEnabled = Preferences.isViewerHoldToZoom();
        autoRotate = Preferences.isViewerAutoRotate();
        displayMode = Preferences.getContentDisplayMode(bookPreferences);
        viewerOrientation = Preferences.getContentOrientation(bookPreferences);
        isSmoothRendering = Preferences.isContentSmoothRendering(bookPreferences);
        int doubleTapZoomCapCode = Preferences.getViewerCapTapZoom();
        if (Preferences.Constant.VIEWER_CAP_TAP_ZOOM_NONE == doubleTapZoomCapCode)
            doubleTapZoomCap = -1;
        else doubleTapZoomCap = doubleTapZoomCapCode;
    }

    public void setRecyclerView(RecyclerView v) {
        recyclerView = v;
    }

    public void setItemTouchListener(View.OnTouchListener itemTouchListener) {
        this.itemTouchListener = itemTouchListener;
    }

    private int getImageType(ImageFile img) {
        if (null == img) return IMG_TYPE_OTHER;

        String extension = FileHelper.getExtension(img.getFileUri());
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

        if (IMG_TYPE_GIF == imageType || IMG_TYPE_APNG == imageType) return ViewType.DEFAULT;
        if (Preferences.Constant.VIEWER_DISPLAY_STRETCH == displayMode)
            return ViewType.IMAGEVIEW_STRETCH;
        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation)
            return ViewType.SSIV_VERTICAL;
        return ViewType.DEFAULT;
    }


    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        View view = inflater.inflate(R.layout.item_viewer_image, viewGroup, false);
        if (ViewType.DEFAULT == viewType) {
            // ImageView shouldn't react to click events when in vertical mode (controlled by ZoomableFrame / ZoomableRecyclerView)
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
                View image = view.findViewById(R.id.imageview);
                image.setClickable(false);
                image.setFocusable(false);
            }
        } else if (ViewType.IMAGEVIEW_STRETCH == viewType) {
            ImageView image = view.findViewById(R.id.imageview);
            image.setScaleType(ImageView.ScaleType.FIT_XY);
        } else if (ViewType.SSIV_VERTICAL == viewType) {
            CustomSubsamplingScaleImageView image = view.findViewById(R.id.ssiv);
            image.setIgnoreTouchEvents(true);
            image.setDirection(CustomSubsamplingScaleImageView.Direction.VERTICAL);
        }

        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation)
            view.setMinimumHeight(PAGE_MIN_HEIGHT); // Avoid stacking 0-px tall images on screen and load all of them at the same time

        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) { // TODO make all that method less ugly
        Timber.d("Picture %d : BindViewHolder", position);
        int imageType = getImageType(getImageAt(position));

        if (holder.forceImageView != null) {
            holder.switchImageView(holder.forceImageView);
            holder.forceImageView = null; // Reset force flag
        } else holder.switchImageView(IMG_TYPE_GIF == imageType || IMG_TYPE_APNG == imageType);

        if (holder.getItemViewType() == ViewType.DEFAULT && Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == viewerOrientation && !holder.isImageView) {
            if (recyclerView != null)
                holder.ssiv.setPreloadDimensions(recyclerView.getWidth(), recyclerView.getHeight());
            if (!Preferences.isViewerZoomTransitions())
                holder.ssiv.setDoubleTapZoomDuration(10);
            holder.ssiv.setOffsetLeftSide(isScrollLTR);
        } else holder.switchImageView(holder.getItemViewType() == ViewType.IMAGEVIEW_STRETCH);

        int layoutStyle = (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        ViewGroup.LayoutParams layoutParams = holder.imgView.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = layoutStyle;
        holder.imgView.setLayoutParams(layoutParams);

        if (Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == viewerOrientation)
            holder.imgView.setOnTouchListener(itemTouchListener);

        boolean imageAvailable = true;
        ImageFile img = getImageAt(position);
        if (img != null && !img.getFileUri().isEmpty()) holder.setImage(img);
        else imageAvailable = false;
        boolean isStreaming = (img != null && img.getStatus().equals(StatusContent.ONLINE) && !imageAvailable);

        if (holder.noImgTxt != null) {
            @StringRes int text = R.string.image_not_found;
            if (isStreaming) text = R.string.image_streaming;
            holder.noImgTxt.setText(text);
            holder.noImgTxt.setVisibility(!imageAvailable ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onViewRecycled(@NonNull ImageViewHolder holder) {
        // Set the holder back to its original constraints while in vertical mode
        // (not doing this will cause super high memory usage by trying to load _all_ images)
        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
            holder.rootView.setMinimumHeight(PAGE_MIN_HEIGHT);

            ViewGroup.LayoutParams layoutParams = holder.rootView.getLayoutParams();
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            holder.rootView.setLayoutParams(layoutParams);
        }

        // Free the SSIV's resources
        if (!holder.isImageView) holder.ssiv.recycle();

        super.onViewRecycled(holder);
    }

    @Nullable
    public ImageFile getImageAt(int index) {
        return (index >= 0 && index < getItemCount()) ? getItem(index) : null;
    }

    public void destroy() {
        if (rs != null) rs.destroy();
    }

    public float getScaleAtPosition(int position) {
        if (recyclerView != null) {
            ImageViewHolder holder = (ImageViewHolder) recyclerView.findViewHolderForAdapterPosition(position);
            if (holder != null) return holder.getScale();
        }
        return 0f;
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

    private static final DiffUtil.ItemCallback<ImageFile> IMAGE_DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ImageFile>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull ImageFile oldItem, @NonNull ImageFile newItem) {
                    return oldItem.uniqueHash() == newItem.uniqueHash();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull ImageFile oldItem, @NonNull ImageFile newItem) {
                    return Objects.equals(oldItem, newItem);
                }
            };

    final class ImageViewHolder extends RecyclerView.ViewHolder implements CustomSubsamplingScaleImageView.OnImageEventListener, RequestListener<Drawable> {

        private final View rootView;
        private final CustomSubsamplingScaleImageView ssiv;
        private final ImageView imageView;
        private final TextView noImgTxt;

        private View imgView;
        private boolean isImageView;
        private Boolean forceImageView = null;

        private ImageFile img;

        private ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            rootView = itemView;

            ssiv = itemView.findViewById(R.id.ssiv);
            imageView = itemView.findViewById(R.id.imageview);
            noImgTxt = itemView.findViewById(R.id.viewer_no_page_txt);
            noImgTxt.setVisibility(View.GONE);
        }

        void setImage(@NonNull ImageFile img) {
            this.img = img;
            int imgType = getImageType(img);
            Uri uri = Uri.parse(img.getFileUri());
            Timber.d("Picture %d : binding viewholder %s %s", getAbsoluteAdapterPosition(), imgType, uri);

            if (!isImageView) { // SubsamplingScaleImageView
                ssiv.recycle();
                ssiv.setMinimumScaleType(getScaleType());
                ssiv.setOnImageEventListener(this);
                ssiv.setLongTapZoomEnabled(longTapZoomEnabled);
                ssiv.setDoubleTapZoomCap(doubleTapZoomCap);
                ssiv.setAutoRotate(autoRotate);
                // 120 dpi = equivalent to the web browser's max zoom level
                ssiv.setMinimumDpi(120);
                ssiv.setDoubleTapZoomDpi(120);
                if (maxBitmapWidth > 0) ssiv.setMaxTileSize(maxBitmapWidth, maxBitmapHeight);
                if (isSmoothRendering)
                    ssiv.setRenderScript(rs);
                else
                    ssiv.setRenderScript(null);
                ssiv.setImage(ImageSource.uri(uri));
            } else { // ImageView
                ImageView view = (ImageView) imgView;
                if (IMG_TYPE_APNG == imgType) {
                    APNGDrawable apngDrawable = new APNGDrawable(new ImgLoader(uri));
                    apngDrawable.registerAnimationCallback(animationCallback);
                    view.setImageDrawable(apngDrawable);
                } else {
                    Glide.with(view)
                            .load(uri)
                            .apply(glideRequestOptions)
                            .listener(this)
                            .into(view);
                }
            }
        }

        private int getScaleType() {
            if (Preferences.Constant.VIEWER_DISPLAY_FILL == displayMode) {
                return CustomSubsamplingScaleImageView.ScaleType.SMART_FILL;
            } else {
                return CustomSubsamplingScaleImageView.ScaleType.CENTER_INSIDE;
            }
        }

        private float getScale() {
            if (!isImageView) {
                return ssiv.getScale();
            } else { // ImageView
                return imageView.getScaleX(); // TODO doesn't work for Glide as it doesn't use ImageView's scaling
            }
        }

        void resetScale() {
            if (!isImageView) {
                if (ssiv.isImageLoaded() && ssiv.isReady() && ssiv.isLaidOut())
                    ssiv.resetScale();
            }
        }

        private void adjustHeight(int imgWidth, int imgHeight, boolean resizeSmallPics) {
            int rootLayoutStyle = (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
            ViewGroup.LayoutParams layoutParams = rootView.getLayoutParams();
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = rootLayoutStyle;
            rootView.setLayoutParams(layoutParams);

            int targetImgHeight = imgHeight;
            // If we display a picture smaller than the screen dimensions, we have to zoom it
            if (resizeSmallPics && imgHeight < SCREEN_HEIGHT && imgWidth < SCREEN_WIDTH) {
                targetImgHeight = Math.round(imgHeight * getTargetScale(imgWidth, imgHeight, displayMode));
                ViewGroup.LayoutParams imgLayoutParams = imgView.getLayoutParams();
                imgLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                imgLayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                imgView.setLayoutParams(imgLayoutParams);
            }

            int targetHeight = targetImgHeight + separatingBarsHeight;
            rootView.setMinimumHeight(targetHeight);
        }

        private float getTargetScale(int imgWidth, int imgHeight, int displayMode) {
            if (Preferences.Constant.VIEWER_DISPLAY_FILL == displayMode) { // Fill screen
                if (imgHeight > imgWidth) {
                    // Fit to width
                    return SCREEN_WIDTH / (float) imgWidth;
                } else {
                    if (SCREEN_HEIGHT > SCREEN_WIDTH)
                        return SCREEN_HEIGHT / (float) imgHeight; // Fit to height when in portrait mode
                    else
                        return SCREEN_WIDTH / (float) imgWidth; // Fit to width when in landscape mode
                }
            } else { // Fit screen
                return Math.min(SCREEN_WIDTH / (float) imgWidth, SCREEN_HEIGHT / (float) imgHeight);
            }
        }

        void switchImageView(boolean isImageView) {
            Timber.d("Picture %d : switching to %s", getAbsoluteAdapterPosition(), isImageView ? "imageView" : "ssiv");
            ssiv.setVisibility(isImageView ? View.GONE : View.VISIBLE);
            imageView.setVisibility(isImageView ? View.VISIBLE : View.GONE);
            imgView = (isImageView) ? imageView : ssiv;
            this.isImageView = isImageView;
        }

        void forceImageView(boolean isImageView) {
            switchImageView(isImageView);
            this.forceImageView = isImageView;
        }

        // == SUBSAMPLINGSCALEVIEW CALLBACKS
        @Override
        public void onReady() {
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
                CustomSubsamplingScaleImageView scaleView = (CustomSubsamplingScaleImageView) imgView;
                adjustHeight(0, (int) (scaleView.getScale() * scaleView.getSHeight()), false);
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
            Timber.d(e, "Picture %d : SSIV loading failed; reloading with Glide : %s", getAbsoluteAdapterPosition(), img.getFileUri());
            // Fall back to Glide
            forceImageView(true);
            // Reload adapter
            notifyItemChanged(getLayoutPosition());
        }

        @Override
        public void onTileLoadError(Throwable e) {
            Timber.d(e, "Picture %d : tileLoad error", getAbsoluteAdapterPosition());
        }

        @Override
        public void onPreviewReleased() {
            // Nothing special
        }


        // == GLIDE CALLBACKS
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            Timber.d(e, "Picture %d : Glide loading failed : %s", getAbsoluteAdapterPosition(), img.getFileUri());
            if (noImgTxt != null) noImgTxt.setVisibility(View.VISIBLE);
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation)
                adjustHeight(resource.getIntrinsicWidth(), resource.getIntrinsicHeight(), true);
            return false;
        }


        // == APNG4Android ANIMATION CALLBACK
        private final Animatable2Compat.AnimationCallback animationCallback = new Animatable2Compat.AnimationCallback() {
            @Override
            public void onAnimationStart(Drawable drawable) {
                if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation)
                    adjustHeight(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), true);
            }
        };
    }

    /**
     * Custom image loaders for APNG4Android to work with files located in SAF area
     */
    static class ImgLoader implements Loader {

        private final Uri uri;

        ImgLoader(Uri uri) {
            this.uri = uri;
        }

        @Override
        public synchronized Reader obtain() throws IOException {
            DocumentFile file = DocumentFile.fromSingleUri(HentoidApp.getInstance().getApplicationContext(), uri);
            if (null == file || !file.exists()) return null; // Not triggered
            return new ImgReader(file.getUri());
        }
    }

    static class ImgReader extends FilterReader {
        private final Uri uri;

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
