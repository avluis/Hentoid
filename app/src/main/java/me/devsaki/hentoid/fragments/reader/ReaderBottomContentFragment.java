package me.devsaki.hentoid.fragments.reader;

import static me.devsaki.hentoid.util.image.ImageHelper.tintBitmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.resource.bitmap.CenterInside;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.databinding.IncludeReaderContentBottomPanelBinding;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.viewmodels.ReaderViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;

public class ReaderBottomContentFragment extends BottomSheetDialogFragment {

    private static final RequestOptions glideRequestOptions;

    private ReaderViewModel viewModel;

    // UI
    private IncludeReaderContentBottomPanelBinding binding = null;
    private final ImageView[] stars = new ImageView[5];

    // VAR
    private int currentRating = -1;


    static {
        Context context = HentoidApp.getInstance();
        int tintColor = ThemeHelper.getColor(context, R.color.light_gray);

        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_hentoid_trans);
        Drawable d = new BitmapDrawable(context.getResources(), tintBitmap(bmp, tintColor));

        final Transformation<Bitmap> centerInside = new CenterInside();
        glideRequestOptions = new RequestOptions()
                .optionalTransform(centerInside)
                .error(d);
    }

    public static void invoke(Context context, FragmentManager fragmentManager) {
        ReaderBottomContentFragment imageBottomSheetFragment = new ReaderBottomContentFragment();
        ThemeHelper.setStyle(context, imageBottomSheetFragment, STYLE_NORMAL, R.style.Theme_Light_BottomSheetDialog);
        imageBottomSheetFragment.show(fragmentManager, "imageBottomSheetFragment");
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(ReaderViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = IncludeReaderContentBottomPanelBinding.inflate(inflater, container, false);

        stars[0] = binding.rating1;
        stars[1] = binding.rating2;
        stars[2] = binding.rating3;
        stars[3] = binding.rating4;
        stars[4] = binding.rating5;

        binding.imgActionFavourite.setOnClickListener(v -> onFavouriteClick());
        for (int i = 0; i < 5; i++) {
            final int rating = i;
            stars[i].setOnClickListener(v -> setRating(rating + 1));
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel.getContent().observe(getViewLifecycleOwner(), this::onContentChanged);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void onContentChanged(Content content) {
        String thumbLocation = content.getCover().getUsableUri();
        if (thumbLocation.isEmpty()) {
            binding.ivCover.setVisibility(View.INVISIBLE);
        } else {
            binding.ivCover.setVisibility(View.VISIBLE);
            if (thumbLocation.startsWith("http")) {
                GlideUrl glideUrl = ContentHelper.bindOnlineCover(content, thumbLocation);
                if (glideUrl != null) {
                    Glide.with(binding.ivCover)
                            .load(glideUrl)
                            .apply(glideRequestOptions)
                            .into(binding.ivCover);
                }
            } else
                Glide.with(binding.ivCover)
                        .load(Uri.parse(thumbLocation))
                        .apply(glideRequestOptions)
                        .into(binding.ivCover);
        }

        binding.contentTitle.setText(content.getTitle());
        binding.contentArtist.setText(ContentHelper.formatArtistForDisplay(requireContext(), content));

        updateFavouriteDisplay(content.isFavourite());
        updateRatingDisplay(content.getRating());

        String tagTxt = ContentHelper.formatTagsForDisplay(content);
        if (tagTxt.isEmpty()) {
            binding.contentTags.setVisibility(View.GONE);
        } else {
            binding.contentTags.setVisibility(View.VISIBLE);
            binding.contentTags.setText(tagTxt);
        }
    }

    private void updateFavouriteDisplay(boolean isFavourited) {
        if (isFavourited)
            binding.imgActionFavourite.setImageResource(R.drawable.ic_fav_full);
        else
            binding.imgActionFavourite.setImageResource(R.drawable.ic_fav_empty);
    }

    private void updateRatingDisplay(int rating) {
        currentRating = rating;
        for (int i = 5; i > 0; i--)
            stars[i - 1].setImageResource(i <= rating ? R.drawable.ic_star_full : R.drawable.ic_star_empty);
    }

    private void setRating(int rating) {
        final int targetRating = (currentRating == rating) ? 0 : rating;
        viewModel.setContentRating(targetRating, this::updateRatingDisplay);
    }

    private void onFavouriteClick() {
        viewModel.toggleContentFavourite(this::updateFavouriteDisplay);
    }
}
