package me.devsaki.hentoid.adapters;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.ImageLoaderThreadExecutor;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;


public final class ImageRecyclerAdapter extends RecyclerView.Adapter<ImageRecyclerAdapter.ImageViewHolder> {

    // TODO : SubsamplingScaleImageView does _not_ support animated GIFs -> use pl.droidsonroids.gif:android-gif-drawable when serving a GIF ?

    private static final int TYPE_OTHER = 0;
    private static final int TYPE_GIF = 1;

    private static final Executor executor = new ImageLoaderThreadExecutor();
    private final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    private View.OnTouchListener itemTouchListener;

    private List<String> imageUris;


    @Override
    public int getItemCount() {
        return imageUris.size();
    }

    public void setImageUris(List<String> imageUris) {
        this.imageUris = Collections.unmodifiableList(imageUris);
    }

    public void setItemTouchListener(View.OnTouchListener itemTouchListener) {
        this.itemTouchListener = itemTouchListener;
    }

    @Override
    public int getItemViewType(int position) {
        if ("gif".equals(FileHelper.getExtension(imageUris.get(position)).toLowerCase())) {
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
        } else {
            view = inflater.inflate(R.layout.item_viewer_image_subsampling, viewGroup, false);
        }
        return new ImageViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder viewHolder, int pos) {
        viewHolder.setImageUri(imageUris.get(pos));

        int layoutStyle = (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == Preferences.getViewerOrientation()) ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;

        ViewGroup.LayoutParams layoutParams = viewHolder.imgView.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = layoutStyle;
        viewHolder.imgView.setLayoutParams(layoutParams);
    }

    final class ImageViewHolder extends RecyclerView.ViewHolder {

        private final int imgType;
        private final View imgView;

        private ImageViewHolder(@NonNull View itemView, int imageType) {
            super(itemView);
            imgType = imageType;
            imgView = itemView;
            imgView.setOnTouchListener(itemTouchListener);

            if (TYPE_OTHER == imgType) ((SubsamplingScaleImageView) imgView).setExecutor(executor);
        }

        void setImageUri(String uri) {
Timber.i(">>>>IMG %s %s", imgType, uri);
            if (TYPE_GIF == imgType) {
                ImageView view = (ImageView) imgView;
                Glide.with(imgView.getContext())
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
    }
}
