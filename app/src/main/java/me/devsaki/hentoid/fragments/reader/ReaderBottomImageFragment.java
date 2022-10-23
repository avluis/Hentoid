package me.devsaki.hentoid.fragments.reader;

import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;
import static me.devsaki.hentoid.util.image.ImageHelper.tintBitmap;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.integration.webp.decoder.WebpDrawable;
import com.bumptech.glide.integration.webp.decoder.WebpDrawableTransformation;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.resource.bitmap.CenterInside;
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
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.databinding.IncludeReaderImageBottomPanelBinding;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.exception.ContentNotProcessedException;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.viewmodels.ReaderViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import timber.log.Timber;

public class ReaderBottomImageFragment extends BottomSheetDialogFragment {

    private static final RequestOptions glideRequestOptions;

    private ReaderViewModel viewModel;

    // UI
    private IncludeReaderImageBottomPanelBinding binding = null;

    // Variables
    private int imageIndex = -1;
    private float scale = -1;
    private ImageFile image = null;


    static {
        Context context = HentoidApp.getInstance();
        int tintColor = ThemeHelper.getColor(context, R.color.light_gray);

        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_hentoid_trans);
        Drawable d = new BitmapDrawable(context.getResources(), tintBitmap(bmp, tintColor));

        final Transformation<Bitmap> centerInside = new CenterInside();
        glideRequestOptions = new RequestOptions()
                .optionalTransform(centerInside)
                .optionalTransform(WebpDrawable.class, new WebpDrawableTransformation(centerInside))
                .error(d);
    }

    public static void invoke(Context context, FragmentManager fragmentManager, int imageIndex, float currentScale) {
        ReaderActivityBundle builder = new ReaderActivityBundle();

        builder.setImageIndex(imageIndex);
        builder.setScale(currentScale);

        ReaderBottomImageFragment imageBottomSheetFragment = new ReaderBottomImageFragment();
        imageBottomSheetFragment.setArguments(builder.getBundle());
        ThemeHelper.setStyle(context, imageBottomSheetFragment, STYLE_NORMAL, R.style.Theme_Light_BottomSheetDialog);
        imageBottomSheetFragment.show(fragmentManager, "imageBottomSheetFragment");
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Bundle bundle = getArguments();
        if (bundle != null) {
            ReaderActivityBundle parser = new ReaderActivityBundle(bundle);
            imageIndex = parser.getImageIndex();
            if (-1 == imageIndex) throw new IllegalArgumentException("Initialization failed");
            scale = parser.getScale();
        }

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(ReaderViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = IncludeReaderImageBottomPanelBinding.inflate(inflater, container, false);

        binding.imgActionFavourite.setOnClickListener(v -> onFavouriteClick());
        binding.imgActionCopy.setOnClickListener(v -> onCopyClick());
        binding.imgActionShare.setOnClickListener(v -> onShareClick());
        binding.imgActionDelete.setOnClickListener(v -> onDeleteClick());

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel.getViewerImages().observe(getViewLifecycleOwner(), this::onImagesChanged);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
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
        binding.imagePath.setText(filePath);

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
            binding.imageStats.setText(getResources().getString(R.string.viewer_img_details, dimensions.x, dimensions.y, scale * 100, sizeStr));
            Glide.with(binding.ivThumb)
                    .load(Uri.parse(image.getFileUri()))
                    .apply(glideRequestOptions)
                    .into(binding.ivThumb);
        } else {
            binding.imageStats.setText(R.string.image_not_found);
            binding.imgActionFavourite.setImageTintList(ColorStateList.valueOf(grayColor));
            binding.imgActionFavourite.setEnabled(false);
            binding.imgActionCopy.setImageTintList(ColorStateList.valueOf(grayColor));
            binding.imgActionCopy.setEnabled(false);
            binding.imgActionShare.setImageTintList(ColorStateList.valueOf(grayColor));
            binding.imgActionShare.setEnabled(false);
        }

        // Don't allow deleting the image if it is archived
        if (isArchive) {
            binding.imgActionDelete.setImageTintList(ColorStateList.valueOf(grayColor));
            binding.imgActionDelete.setEnabled(false);
        } else {
            binding.imgActionDelete.setImageTintList(null);
            binding.imgActionDelete.setEnabled(true);
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
            binding.imgActionFavourite.setImageResource(R.drawable.ic_fav_full);
        else
            binding.imgActionFavourite.setImageResource(R.drawable.ic_fav_empty);
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

            Snackbar.make(binding.getRoot(), R.string.copy_download_folder_success, LENGTH_LONG)
                    .setAction(R.string.open_folder, v -> FileHelper.openFile(requireContext(), FileHelper.getDownloadsFolder()))
                    .show();
        } catch (IOException | IllegalArgumentException e) {
            Snackbar.make(binding.getRoot(), R.string.copy_download_folder_fail, LENGTH_LONG).show();
        }
    }

    /**
     * Handle click on "Share" action button
     */
    private void onShareClick() {
        Uri fileUri = Uri.parse(image.getFileUri());
        if (FileHelper.fileExists(requireContext(), fileUri))
            FileHelper.shareFile(requireContext(), fileUri, "");
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
            Snackbar.make(binding.getRoot(), message, BaseTransientBottomBar.LENGTH_LONG).show();
        }
    }
}
