package me.devsaki.hentoid.fragments.reader;

import static me.devsaki.hentoid.util.ImageHelper.tintBitmap;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.integration.webp.decoder.WebpDrawable;
import com.bumptech.glide.integration.webp.decoder.WebpDrawableTransformation;
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

        String tagTxt = ContentHelper.formatTagsForDisplay(content);
        if (tagTxt.isEmpty()) {
            binding.contentTags.setVisibility(View.GONE);
        } else {
            binding.contentTags.setVisibility(View.VISIBLE);
            binding.contentTags.setText(tagTxt);
        }
    }
}
