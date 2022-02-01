package me.devsaki.hentoid.fragments.viewer;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;
import static me.devsaki.hentoid.util.ImageHelper.tintBitmap;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.exception.ContentNotProcessedException;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import timber.log.Timber;

public class ViewerBottomImageFragment extends BottomSheetDialogFragment {

    private static final RequestOptions glideRequestOptions;

    private ImageViewerViewModel viewModel;

    private int imageIndex = -1;
    private float scale = -1;
    private ImageFile image = null;

    // UI
    private View rootView;
    private ImageView imgThumb;
    private TextView imgPath;
    private TextView imgStats;

    private ImageView favoriteButton;
    private ImageView copyButton;
    private ImageView shareButton;
    private ImageView deleteButton;


    static {
        Context context = HentoidApp.getInstance();
        int tintColor = ThemeHelper.getColor(context, R.color.light_gray);

        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_hentoid_trans);
        Drawable d = new BitmapDrawable(context.getResources(), tintBitmap(bmp, tintColor));

        glideRequestOptions = new RequestOptions()
                .centerInside()
                .error(d);
    }

    public static void invoke(Context context, FragmentManager fragmentManager, int imageIndex, float currentScale) {
        ImageViewerActivityBundle.Builder builder = new ImageViewerActivityBundle.Builder();

        builder.setImageIndex(imageIndex);
        builder.setScale(currentScale);

        ViewerBottomImageFragment imageBottomSheetFragment = new ViewerBottomImageFragment();
        imageBottomSheetFragment.setArguments(builder.getBundle());
        ThemeHelper.setStyle(context, imageBottomSheetFragment, STYLE_NORMAL, R.style.Theme_Light_BottomSheetDialog);
        imageBottomSheetFragment.show(fragmentManager, "imageBottomSheetFragment");
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Bundle bundle = getArguments();
        if (bundle != null) {
            ImageViewerActivityBundle.Parser parser = new ImageViewerActivityBundle.Parser(bundle);
            imageIndex = parser.getImageIndex();
            if (-1 == imageIndex) throw new IllegalArgumentException("Initialization failed");
            scale = parser.getScale();
        }

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(ImageViewerViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.include_viewer_image_info, container, false);

        imgThumb = requireViewById(rootView, R.id.ivThumb);
        imgPath = requireViewById(rootView, R.id.image_path);
        imgStats = requireViewById(rootView, R.id.image_stats);

        favoriteButton = requireViewById(rootView, R.id.img_action_favourite);
        favoriteButton.setOnClickListener(v -> onFavouriteClick());

        copyButton = requireViewById(rootView, R.id.img_action_copy);
        copyButton.setOnClickListener(v -> onCopyClick());

        shareButton = requireViewById(rootView, R.id.img_action_share);
        shareButton.setOnClickListener(v -> onShareClick());

        deleteButton = requireViewById(rootView, R.id.img_action_delete);
        deleteButton.setOnClickListener(v -> onDeleteClick());

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel.getViewerImages().observe(getViewLifecycleOwner(), this::onImagesChanged);
    }


    /**
     * Observer for changes in the book's list of images
     *
     * @param images Book's list of images
     */
    private void onImagesChanged(List<ImageFile> images) {
        int grayColor = ThemeHelper.getColor(requireContext(), R.color.dark_gray);

        if (imageIndex >= images.size())
            imageIndex = images.size() - 1; // Might happen when deleting the last page
        image = images.get(imageIndex);

        String filePath;
        boolean isArchive = image.getContent().getTarget().isArchive();
        if (isArchive) {
            filePath = image.getUrl();
            int lastSeparator = filePath.lastIndexOf('/');
            String archiveUri = filePath.substring(0, lastSeparator);
            String fileName = filePath.substring(lastSeparator);
            filePath = FileHelper.getFullPathFromUri(requireContext(), Uri.parse(archiveUri)) + fileName;
        } else {
            filePath = FileHelper.getFullPathFromUri(requireContext(), Uri.parse(image.getFileUri()));
        }
        imgPath.setText(filePath);

        boolean imageExists = FileHelper.fileExists(requireContext(), Uri.parse(image.getFileUri()));
        if (imageExists) {
            Point dimensions = getImageDimensions(requireContext(), image.getFileUri());
            String sizeStr;
            if (image.getSize() > 0) {
                sizeStr = FileHelper.formatHumanReadableSize(image.getSize(), getResources());
            } else {
                long size = FileHelper.fileSizeFromUri(requireContext(), Uri.parse(image.getFileUri()));
                sizeStr = FileHelper.formatHumanReadableSize(size, getResources());
            }
            imgStats.setText(getResources().getString(R.string.viewer_img_details, dimensions.x, dimensions.y, scale * 100, sizeStr));
            Glide.with(imgThumb)
                    .load(Uri.parse(image.getFileUri()))
                    .apply(glideRequestOptions)
                    .into(imgThumb);
        } else {
            imgStats.setText(R.string.image_not_found);
            favoriteButton.setImageTintList(ColorStateList.valueOf(grayColor));
            favoriteButton.setEnabled(false);
            copyButton.setImageTintList(ColorStateList.valueOf(grayColor));
            copyButton.setEnabled(false);
            shareButton.setImageTintList(ColorStateList.valueOf(grayColor));
            shareButton.setEnabled(false);
        }

        // Don't allow deleting the image if it is archived
        if (isArchive) {
            deleteButton.setImageTintList(ColorStateList.valueOf(grayColor));
            deleteButton.setEnabled(false);
        } else {
            deleteButton.setImageTintList(null);
            deleteButton.setEnabled(true);
        }

        updateFavouriteDisplay(image.isFavourite());
    }

    /**
     * Handle click on "Favourite" action button
     */
    private void onFavouriteClick() {
        viewModel.toggleImageFavourite(imageIndex, this::onToggleFavouriteSuccess);
    }

    /**
     * Success callback when the new favourite'd state has been successfully persisted
     */
    private void onToggleFavouriteSuccess(Boolean newState) {
        image.setFavourite(newState);
        updateFavouriteDisplay(newState);
    }

    /**
     * Update the display of the "favourite page" action button
     *
     * @param isFavourited True if the button has to represent a favourite page; false instead
     */
    private void updateFavouriteDisplay(boolean isFavourited) {
        if (isFavourited)
            favoriteButton.setImageResource(R.drawable.ic_fav_full);
        else
            favoriteButton.setImageResource(R.drawable.ic_fav_empty);
    }

    /**
     * Handle click on "Copy" action button
     */
    private void onCopyClick() {
        String targetFileName = image.getContent().getTarget().getUniqueSiteId() + "-" + image.getName() + "." + FileHelper.getExtension(image.getFileUri());
        try {
            Uri fileUri = Uri.parse(image.getFileUri());
            if (!FileHelper.fileExists(requireContext(), fileUri)) return;

            try (OutputStream newDownload = FileHelper.openNewDownloadOutputStream(requireContext(), targetFileName, image.getMimeType())) {
                try (InputStream input = FileHelper.getInputStream(requireContext(), fileUri)) {
                    Helper.copy(input, newDownload);
                }
            }

            Snackbar.make(rootView, R.string.copy_download_folder_success, LENGTH_LONG)
                    .setAction(R.string.open_folder, v -> FileHelper.openFile(requireContext(), FileHelper.getDownloadsFolder()))
                    .show();
        } catch (IOException | IllegalArgumentException e) {
            Snackbar.make(rootView, R.string.copy_download_folder_fail, LENGTH_LONG).show();
        }
    }

    /**
     * Handle click on "Share" action button
     */
    private void onShareClick() {
        Uri fileUri = Uri.parse(image.getFileUri());
        if (FileHelper.fileExists(requireContext(), fileUri))
            FileHelper.shareFile(requireContext(), fileUri, "Share picture");
    }

    /**
     * Handle click on "Delete" action button
     */
    private void onDeleteClick() {
        new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                .setIcon(R.drawable.ic_warning)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(R.string.viewer_ask_delete_page)
                .setPositiveButton(R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            viewModel.deletePage(imageIndex, this::onDeleteError);
                        })
                .setNegativeButton(R.string.no,
                        (dialog12, which) -> dialog12.dismiss())
                .create()
                .show();
    }

    /**
     * Return the given image's dimensions
     *
     * @param context Context to be used
     * @param uri     Uri of the image to be read
     * @return Dimensions (x,y) of the given image
     */
    private static Point getImageDimensions(@NonNull final Context context, @NonNull final String uri) {
        Uri fileUri = Uri.parse(uri);
        if (!FileHelper.fileExists(context, fileUri)) return new Point(0, 0);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeStream(FileHelper.getInputStream(context, fileUri), null, options);
            return new Point(options.outWidth, options.outHeight);
        } catch (IOException | IllegalArgumentException e) {
            Timber.w(e);
            return new Point(0, 0);
        }
    }

    /**
     * Callback for the failure of the "delete item" action
     */
    private void onDeleteError(Throwable t) {
        Timber.e(t);
        if (t instanceof ContentNotProcessedException) {
            ContentNotProcessedException e = (ContentNotProcessedException) t;
            String message = (null == e.getMessage()) ? getResources().getString(R.string.file_removal_failed) : e.getMessage();
            Snackbar.make(rootView, message, BaseTransientBottomBar.LENGTH_LONG).show();
        }
    }
}
