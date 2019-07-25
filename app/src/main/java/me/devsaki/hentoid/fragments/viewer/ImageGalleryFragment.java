package me.devsaki.hentoid.fragments.viewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.adapters.ImageGalleryAdapter;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.viewholders.ImageFileFlex;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;

import static androidx.core.view.ViewCompat.requireViewById;

public class ImageGalleryFragment extends Fragment {

    private static final String KEY_FILTER_FAVOURITES = "filter_favourites";

    private ImageGalleryAdapter galleryImagesAdapter;
    private ImageViewerViewModel viewModel;
    private MenuItem favouritesFilterMenu;

    private Boolean filterFavourites = false;


    static ImageGalleryFragment newInstance(boolean filterFavourites) {
        ImageGalleryFragment fragment = new ImageGalleryFragment();
        Bundle args = new Bundle();
        args.putBoolean(KEY_FILTER_FAVOURITES, filterFavourites);
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_viewer_gallery, container, false);

        Bundle arguments = getArguments();
        if (arguments != null)
            filterFavourites = arguments.getBoolean(KEY_FILTER_FAVOURITES, false);

        setHasOptionsMenu(true);
        viewModel = ViewModelProviders.of(requireActivity()).get(ImageViewerViewModel.class);

        initUI(view);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel.getImages()
                .observe(this, this::onImagesChanged);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            requireActivity().onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.gallery_menu_action_favourites) {
            toggleFavouritesDisplay();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.gallery_menu, menu);
        favouritesFilterMenu = menu.findItem(R.id.gallery_menu_action_favourites);
        updateFavouriteDisplay();
    }

    private void initUI(View rootView) {
        Toolbar toolbar = requireViewById(rootView, R.id.viewer_gallery_toolbar);
        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
        toolbar.setTitle("Gallery");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        galleryImagesAdapter = new ImageGalleryAdapter(null, this::onFavouriteClick);
        galleryImagesAdapter.addListener((FlexibleAdapter.OnItemClickListener) this::onItemClick);
        RecyclerView releaseDescription = requireViewById(rootView, R.id.viewer_gallery_recycler);
        releaseDescription.setAdapter(galleryImagesAdapter);
    }

    private void onImagesChanged(List<ImageFile> images) {
        for (ImageFile img : images) galleryImagesAdapter.addItem(new ImageFileFlex(img));
    }

    private boolean onItemClick(View view, int position) {
        ImageFileFlex imgFileFlex = (ImageFileFlex) galleryImagesAdapter.getItem(position);
        if (imgFileFlex != null)
            viewModel.setStartingIndex(imgFileFlex.getItem().getDisplayOrder());
        requireActivity().onBackPressed();
        return true;
    }

    private void onFavouriteClick(ImageFile img) {
        viewModel.togglePageFavourite(img, this::onFavouriteSuccess);
    }

    private void onFavouriteSuccess(ImageFile img) {
        if (filterFavourites) {
            // Reset favs filter if no favourite page remains
            if (!galleryImagesAdapter.isFavouritePresent()) {
                filterFavourites = false;
                galleryImagesAdapter.setFilter(filterFavourites);
                galleryImagesAdapter.filterItems();
                if (galleryImagesAdapter.getItemCount() > 0) galleryImagesAdapter.smoothScrollToPosition(0);
            } else {
                galleryImagesAdapter.notifyDataSetChanged(); // Because no easy way to spot which item has changed when the view is filtered
            }
        } else galleryImagesAdapter.notifyItemChanged(img.getDisplayOrder());

        favouritesFilterMenu.setVisible(galleryImagesAdapter.isFavouritePresent());
    }

    private void toggleFavouritesDisplay() {
        filterFavourites = !filterFavourites;
        updateFavouriteDisplay();
    }

    private void updateFavouriteDisplay() {
        favouritesFilterMenu.setVisible(galleryImagesAdapter.isFavouritePresent());
        favouritesFilterMenu.setIcon(filterFavourites ? R.drawable.ic_fav_full : R.drawable.ic_fav_empty);
        galleryImagesAdapter.setFilter(filterFavourites);
        galleryImagesAdapter.filterItems();
        if (galleryImagesAdapter.getItemCount() > 0) galleryImagesAdapter.smoothScrollToPosition(0);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_FILTER_FAVOURITES, filterFavourites);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null)
            filterFavourites = savedInstanceState.getBoolean(KEY_FILTER_FAVOURITES, false);
    }
}
