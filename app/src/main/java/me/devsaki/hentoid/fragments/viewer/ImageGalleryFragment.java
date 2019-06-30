package me.devsaki.hentoid.fragments.viewer;

import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.adapters.ImageGalleryAdapter;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.viewholders.ImageFileFlex;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;

import static android.support.v4.view.ViewCompat.requireViewById;

public class ImageGalleryFragment extends Fragment {

    private ImageGalleryAdapter galleryImagesAdapter;
    private ImageViewerViewModel viewModel;
    private MenuItem bookmarkFilterMenu;

    private Boolean filterBookmarks = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_viewer_gallery, container, false);

        setHasOptionsMenu(true);
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
        } else if (item.getItemId() == R.id.gallery_menu_action_bookmarks) {
            toggleBookmarkDisplay();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.gallery_menu, menu);
        bookmarkFilterMenu = menu.findItem(R.id.gallery_menu_action_bookmarks);
    }

    private void initUI(View rootView) {
        Toolbar toolbar = requireViewById(rootView, R.id.viewer_gallery_toolbar);
        ((AppCompatActivity)requireActivity()).setSupportActionBar(toolbar);
        toolbar.setTitle("Gallery");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        galleryImagesAdapter = new ImageGalleryAdapter(null, this::onBookmarkClick);
        galleryImagesAdapter.addListener((FlexibleAdapter.OnItemClickListener) this::onItemClick);
        RecyclerView releaseDescription = requireViewById(rootView, R.id.viewer_gallery_recycler);
        releaseDescription.setAdapter(galleryImagesAdapter);
    }

    private void onImagesChanged(List<ImageFile> images) {
        for (ImageFile img : images) galleryImagesAdapter.addItem(new ImageFileFlex(img));
    }

    private boolean onItemClick(View view, int position) {
        ImageFileFlex imgFileFlex = (ImageFileFlex)galleryImagesAdapter.getItem(position);
        if (imgFileFlex != null) viewModel.setImageIndex(imgFileFlex.getItem().getDisplayOrder());
        requireActivity().onBackPressed();
        return true;
    }

    private void onBookmarkClick(ImageFile img) {
        viewModel.togglePageBookmark(img, this::onBookmarkSuccess);
    }

    private void onBookmarkSuccess(ImageFile img) {
        galleryImagesAdapter.notifyItemChanged(img.getDisplayOrder()); // TODO : does not work when gallery view is filtered
    }

    private void toggleBookmarkDisplay()
    {
        filterBookmarks = !filterBookmarks;
        bookmarkFilterMenu.setIcon(filterBookmarks?R.drawable.ic_action_bookmark_on:R.drawable.ic_action_bookmark_off);
        galleryImagesAdapter.setFilter(filterBookmarks);
        galleryImagesAdapter.filterItems();
    }
}
