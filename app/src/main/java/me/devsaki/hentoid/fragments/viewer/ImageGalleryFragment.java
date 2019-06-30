package me.devsaki.hentoid.fragments.viewer;

import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.IFlexible;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.DrawerItem;
import me.devsaki.hentoid.viewholders.ImageFileFlex;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;

import static android.support.v4.view.ViewCompat.requireViewById;

public class ImageGalleryFragment extends Fragment {

    private FlexibleAdapter<IFlexible> galleryImagesAdapter;
    private ImageViewerViewModel viewModel;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_viewer_gallery, container, false);

        viewModel = ViewModelProviders.of(requireActivity()).get(ImageViewerViewModel.class);

        initUI(view);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel
                .getImages()
                .observe(this, this::onImagesChanged);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            requireActivity().onBackPressed();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void initUI(View rootView) {
        galleryImagesAdapter = new FlexibleAdapter<>(null);
        galleryImagesAdapter.addListener((FlexibleAdapter.OnItemClickListener) this::onItemClick);
        RecyclerView releaseDescription = requireViewById(rootView, R.id.viewer_gallery_recycler);
        releaseDescription.setAdapter(galleryImagesAdapter);
    }

    private void onImagesChanged(List<ImageFile> images) {
        for (ImageFile img : images) galleryImagesAdapter.addItem(new ImageFileFlex(img));
    }

    private boolean onItemClick(View view, int position) {
        viewModel.setImageIndex(position);
        requireActivity().onBackPressed();
        return true;
    }

    private void onBookmarkClick() {
        viewModel.toggleCurrentPageBookmark(this::onBookmarkSuccess);
    }

    private void onBookmarkSuccess(ImageFile img) {
        // Check if the updated image is still the one displayed on screen
        ImageFile currentImage = viewModel.getImage(viewModel.getImageIndex());
        if (currentImage != null && img.getId() == currentImage.getId()) {
            updateBookmarkDisplay();
        }
    }

    private void updateBookmarkDisplay() {
        ImageFile currentImage = viewModel.getImage(viewModel.getImageIndex());
        if (currentImage != null) {
            /*
            if (currentImage.isBookmarked()) {
                pageBookmarkButton.setImageResource(R.drawable.ic_action_bookmark_on);
                pageBookmarkText.setText(R.string.viewer_bookmark_on);
            } else {
                pageBookmarkButton.setImageResource(R.drawable.ic_action_bookmark_off);
                pageBookmarkText.setText(R.string.viewer_bookmark_off);
            }
            */
        }
    }
}
