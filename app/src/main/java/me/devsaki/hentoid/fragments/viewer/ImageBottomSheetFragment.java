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

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;

import static androidx.core.view.ViewCompat.requireViewById;

public class ImageBottomSheetFragment extends BottomSheetDialogFragment {

    private ImageViewerViewModel viewModel;

    private int imageIndex = -1;
    private ImageFile image = null;

    // UI
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
        View rootView = inflater.inflate(R.layout.include_viewer_image_info, container, false);

        imgPath = requireViewById(rootView, R.id.image_path);
        imgDimensions = requireViewById(rootView, R.id.image_dimensions);

        favoriteButton = requireViewById(rootView, R.id.img_action_favourite);
        favoriteButton.setOnClickListener(v -> onFavouriteClick());

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel.getImages().observe(getViewLifecycleOwner(), this::onImagesChanged);
    }

    @Override
    public void onResume() {
        super.onResume();
        // TODO keep ?
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // TODO keep ?
    }

    /**
     * Observer for changes in the book's list of images
     *
     * @param images Book's list of images
     */
    private void onImagesChanged(List<ImageFile> images) {
        image = images.get(imageIndex);

        imgPath.setText(image.getAbsolutePath());
        Point size = getIMGSize(image.getAbsolutePath());
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

    private static Point getIMGSize(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        return new Point(options.outWidth, options.outHeight);
    }
}
