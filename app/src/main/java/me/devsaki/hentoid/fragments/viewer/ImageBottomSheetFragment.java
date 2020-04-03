package me.devsaki.hentoid.fragments.viewer;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Point;
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

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;

public class ImageBottomSheetFragment extends BottomSheetDialogFragment {

    private ImageViewerViewModel viewModel;

    private int imageIndex = -1;
    private ImageFile image = null;

    // UI
    private View rootView;
    private TextView imgPath;
    private TextView imgDimensions;

    private ImageView favoriteButton;


    public static void show(Context context, FragmentManager fragmentManager, int imageIndex) {
        ImageViewerActivityBundle.Builder builder = new ImageViewerActivityBundle.Builder();

        builder.setImageIndex(imageIndex);

        ImageBottomSheetFragment imageBottomSheetFragment = new ImageBottomSheetFragment();
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
        }

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(ImageViewerViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.include_viewer_image_info, container, false);

        imgPath = requireViewById(rootView, R.id.image_path);
        imgDimensions = requireViewById(rootView, R.id.image_dimensions);

        favoriteButton = requireViewById(rootView, R.id.img_action_favourite);
        favoriteButton.setOnClickListener(v -> onFavouriteClick());

        View copyButton = requireViewById(rootView, R.id.img_action_copy);
        copyButton.setOnClickListener(v -> onCopyClick());

        View shareButton = requireViewById(rootView, R.id.img_action_share);
        shareButton.setOnClickListener(v -> onShareClick());

        View deleteButton = requireViewById(rootView, R.id.img_action_delete);
        deleteButton.setOnClickListener(v -> onDeleteClick());

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel.getImages().observe(getViewLifecycleOwner(), this::onImagesChanged);
    }

    /**
     * Observer for changes in the book's list of images
     *
     * @param images Book's list of images
     */
    private void onImagesChanged(List<ImageFile> images) {
        image = images.get(imageIndex);

        imgPath.setText(image.getAbsolutePath());
        Point size = getImageSize(image.getAbsolutePath());
        imgDimensions.setText(String.format("%s x %s", size.x, size.y));

        updateFavouriteDisplay(image.isFavourite());
    }

    /**
     * Handle click on "Favourite" action button
     */
    private void onFavouriteClick() {
        viewModel.togglePageFavourite(image, this::onFavouriteSuccess);
    }

    /**
     * Success callback when the new favourite'd state has been successfully persisted
     *
     * @param img The favourite'd / unfavourite'd ImageFile in its new state
     */
    private void onFavouriteSuccess(ImageFile img) {
        // Check if the updated image is still the one displayed on screen
        if (img.getId() == image.getId())
            image.setFavourite(img.isFavourite());
        updateFavouriteDisplay(img.isFavourite());
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
        String targetFileName = image.content.getTarget().getUniqueSiteId() + "-" + image.getName() + "." + FileHelper.getExtension(image.getAbsolutePath());
        try {
            File sourceFile = new File(image.getAbsolutePath());
            try (OutputStream newDownload = FileHelper.openNewDownloadOutputStream(targetFileName)) {
                try (InputStream input = FileHelper.getInputStream(sourceFile)) {
                    FileHelper.copy(input, newDownload);
                }
            }

            Snackbar.make(rootView, R.string.viewer_copy_success, LENGTH_LONG)
                    .setAction("OPEN FOLDER", v -> FileHelper.openFile(requireContext(), FileHelper.getDownloadsFolder()))
                    .show();
        } catch (IOException e) {
            Snackbar.make(rootView, R.string.viewer_copy_fail, LENGTH_LONG).show();
        }
    }

    /**
     * Handle click on "Share" action button
     */
    private void onShareClick() {
        File sourceFile = new File(image.getAbsolutePath());
        FileHelper.shareFile(requireContext(), sourceFile, "Share picture");
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
                .setPositiveButton(android.R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            viewModel.deletePage(imageIndex);

                        })
                .setNegativeButton(android.R.string.no,
                        (dialog12, which) -> dialog12.dismiss())
                .create()
                .show();
    }

    private static Point getImageSize(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        return new Point(options.outWidth, options.outHeight);
    }
}
