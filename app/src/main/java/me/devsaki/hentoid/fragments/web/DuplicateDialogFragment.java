package me.devsaki.hentoid.fragments.web;

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

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.resource.bitmap.CenterInside;
import com.bumptech.glide.request.RequestOptions;

import org.greenrobot.eventbus.EventBus;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.databinding.DialogWebDuplicateBinding;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.image.ImageHelper;

public final class DuplicateDialogFragment extends DialogFragment {

    private static final String KEY_CONTENT_ID = "contentId";
    private static final String KEY_ONLINE_CONTENT_PAGES = "onlineContentPages";
    private static final String KEY_CONTENT_SIMILARITY = "similarity";
    private static final String KEY_IS_DOWNLOAD_PLUS = "downloadPlus";
    private DialogWebDuplicateBinding binding = null;

    @IntDef({ActionMode.DOWNLOAD, ActionMode.DOWNLOAD_PLUS, ActionMode.REPLACE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionMode {
        // Download book
        int DOWNLOAD = 0;
        // Download new pages
        int DOWNLOAD_PLUS = 1;
        // Replace existing book
        int REPLACE = 2;
    }

    private static final RequestOptions glideRequestOptions;

    static {
        Context context = HentoidApp.Companion.getInstance();

        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_hentoid_trans);
        int tintColor = ThemeHelper.getColor(context, R.color.light_gray);
        Drawable d = new BitmapDrawable(context.getResources(), ImageHelper.INSTANCE.tintBitmap(bmp, tintColor));

        final Transformation<Bitmap> centerInside = new CenterInside();
        glideRequestOptions = new RequestOptions()
                .optionalTransform(centerInside)
                .error(d);
    }

    // === VARIABLES
    private DuplicateDialogFragment.Parent parent;
    private long contentId;
    private int onlineContentPages;
    private float similarity;
    private boolean isDownloadPlus;

    public static void invoke(
            @NonNull final FragmentActivity parent,
            long libraryContentId,
            int onlineContentNbPages,
            float similarity,
            boolean isDownloadPlus) {
        DuplicateDialogFragment fragment = new DuplicateDialogFragment();

        Bundle args = new Bundle();
        args.putLong(KEY_CONTENT_ID, libraryContentId);
        args.putInt(KEY_ONLINE_CONTENT_PAGES, onlineContentNbPages);
        args.putFloat(KEY_CONTENT_SIMILARITY, similarity);
        args.putBoolean(KEY_IS_DOWNLOAD_PLUS, isDownloadPlus);
        fragment.setArguments(args);

        fragment.show(parent.getSupportFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        contentId = getArguments().getLong(KEY_CONTENT_ID, -1);
        if (contentId < 1) throw new IllegalArgumentException("No content ID found");
        onlineContentPages = getArguments().getInt(KEY_ONLINE_CONTENT_PAGES);
        similarity = getArguments().getFloat(KEY_CONTENT_SIMILARITY);
        isDownloadPlus = getArguments().getBoolean(KEY_IS_DOWNLOAD_PLUS, false);

        parent = (Parent) getActivity();
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        super.onDestroyView();
        parent = null;
        binding = null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        binding = DialogWebDuplicateBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        Context context = requireContext();
        Content libraryContent = loadLibraryContent();
        if (null == libraryContent) return;

        binding.subtitle.setText(isDownloadPlus ? R.string.duplicate_alert_subtitle_pages : R.string.duplicate_alert_subtitle_book);
        binding.downloadPlusBtn.setVisibility(isDownloadPlus ? View.VISIBLE : View.GONE);
        binding.chAlwaysDownload.setVisibility(isDownloadPlus ? View.GONE : View.VISIBLE);
        binding.chNeverExtraOnDupes.setVisibility(isDownloadPlus ? View.VISIBLE : View.GONE);

        binding.tvTitle.setText(libraryContent.getTitle());
        binding.tvTitle.setSelected(true);
        ImageFile cover = libraryContent.getCover();
        String thumbLocation = cover.getUsableUri();
        if (thumbLocation.isEmpty()) {
            binding.ivCover.setVisibility(View.INVISIBLE);
        } else {
            binding.ivCover.setVisibility(View.VISIBLE);
            if (thumbLocation.startsWith("http"))
                Glide.with(binding.ivCover)
                        .load(thumbLocation)
                        .apply(glideRequestOptions)
                        .into(binding.ivCover);
            else
                Glide.with(binding.ivCover)
                        .load(Uri.parse(thumbLocation))
                        .apply(glideRequestOptions)
                        .into(binding.ivCover);
        }

        @DrawableRes int resId = ContentHelper.getFlagResourceId(context, libraryContent);
        if (resId != 0) {
            binding.ivFlag.setImageResource(resId);
            binding.ivFlag.setVisibility(View.VISIBLE);
        } else {
            binding.ivFlag.setVisibility(View.GONE);
        }

        binding.tvArtist.setText(ContentHelper.formatArtistForDisplay(context, libraryContent));

        binding.tvPagesLibrary.setVisibility(0 == libraryContent.getQtyPages() ? View.INVISIBLE : View.VISIBLE);
        int stringRes = (ContentHelper.isInQueue(libraryContent.getStatus())) ? R.string.work_pages_duplicate_dialog_queue : R.string.work_pages_duplicate_dialog_library;
        binding.tvPagesLibrary.setText(getResources().getString(stringRes, libraryContent.getQtyPages()));
        binding.tvPagesSource.setVisibility(0 == onlineContentPages ? View.INVISIBLE : View.VISIBLE);
        binding.tvPagesSource.setText(getResources().getString(R.string.work_pages_duplicate_dialog_source, onlineContentPages));

        // Buttons
        Site site = libraryContent.getSite();
        if (site != null && !site.equals(Site.NONE)) {
            int img = site.getIco();
            binding.ivSite.setImageResource(img);
            binding.ivSite.setVisibility(View.VISIBLE);
        } else {
            binding.ivSite.setVisibility(View.GONE);
        }
        binding.ivExternal.setVisibility(libraryContent.getStatus().equals(StatusContent.EXTERNAL) ? View.VISIBLE : View.GONE);
        if (libraryContent.isFavourite()) {
            binding.ivFavourite.setImageResource(R.drawable.ic_fav_full);
        } else {
            binding.ivFavourite.setImageResource(R.drawable.ic_fav_empty);
        }

        // Similarity score
        binding.tvScore.setText(context.getString(R.string.duplicate_alert_similarity, similarity * 100));


        binding.cancelBtn.setOnClickListener(v -> dismissAllowingStateLoss());
        binding.replaceBtn.setOnClickListener(v -> submit(ActionMode.REPLACE));
        binding.downloadBtn.setOnClickListener(v -> submit(ActionMode.DOWNLOAD));
        binding.downloadPlusBtn.setOnClickListener(v -> submit(ActionMode.DOWNLOAD_PLUS));
    }

    @Nullable
    private Content loadLibraryContent() {
        CollectionDAO dao = new ObjectBoxDAO(requireContext());
        try {
            return dao.selectContent(contentId);
        } finally {
            dao.cleanup();
        }
    }

    private void submit(@ActionMode int actionMode) {
        if (binding.chAlwaysDownload.isChecked())
            Preferences.setDownloadDuplicateAsk(false);
        if (binding.chNeverExtraOnDupes.isChecked())
            Preferences.setDownloadDuplicateTry(false);
        parent.onDownloadDuplicate(actionMode);
        dismissAllowingStateLoss();
    }


    public interface Parent {
        void onDownloadDuplicate(@ActionMode int actionMode);
    }
}
