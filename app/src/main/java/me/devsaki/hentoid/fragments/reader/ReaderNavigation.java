package me.devsaki.hentoid.fragments.reader;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;
import com.google.android.material.slider.Slider;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.databinding.FragmentReaderPagerBinding;
import me.devsaki.hentoid.databinding.IncludeReaderControlsOverlayBinding;
import me.devsaki.hentoid.ui.InputDialog;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastHelper;

class ReaderNavigation {

    // == UI
    private FragmentReaderPagerBinding superBinding;
    private IncludeReaderControlsOverlayBinding binding;
    // Bottom bar controls (proxies for left or right position, depending on current reading direction)
    private TextView pageCurrentNumber;
    private TextView pageMaxNumber;

    // == VARS
    private final Pager pager;
    private List<ImageFile> images;
    private List<Chapter> chapters;
    private Chapter currentChapter;
    private int maxPageNumber; // Relative (chapter-scale) max page number


    public ReaderNavigation(Pager pager, FragmentReaderPagerBinding inBinding) {
        superBinding = inBinding;
        binding = inBinding.controlsOverlay;
        this.pager = pager;

        binding.pageSlider.addOnChangeListener((slider1, value, fromUser) -> {
            if (fromUser) {
                int offset = 0;
                Chapter currentChapter = getCurrentChapter();
                if (currentChapter != null) {
                    List<ImageFile> chapImgs = currentChapter.getReadableImageFiles();
                    if (!chapImgs.isEmpty()) offset = chapImgs.get(0).getOrder() - 1;
                }

                pager.seekToPosition(Math.max(0, offset + (int) value));
            }
        });

        binding.pageSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                binding.imagePreviewLeft.setVisibility(View.VISIBLE);
                binding.imagePreviewCenter.setVisibility(View.VISIBLE);
                binding.imagePreviewRight.setVisibility(View.VISIBLE);
                superBinding.recyclerView.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                binding.imagePreviewLeft.setVisibility(View.INVISIBLE);
                binding.imagePreviewCenter.setVisibility(View.INVISIBLE);
                binding.imagePreviewRight.setVisibility(View.INVISIBLE);
                superBinding.recyclerView.setVisibility(View.VISIBLE);
            }
        });
    }

    void clear() {
        binding = null;
        superBinding = null;
    }

    /**
     * Update the visibility of "next/previous book" buttons
     *
     * @param content Current book
     */
    void onContentChanged(@Nonnull Content content) {
        int direction = Preferences.getContentDirection(content.getBookPreferences());
        ImageButton nextButton = (Preferences.Constant.VIEWER_DIRECTION_LTR == direction) ? binding.viewerNextBookBtn : binding.viewerPrevBookBtn;
        ImageButton prevButton = (Preferences.Constant.VIEWER_DIRECTION_LTR == direction) ? binding.viewerPrevBookBtn : binding.viewerNextBookBtn;

        prevButton.setVisibility(content.isFirst() ? View.INVISIBLE : View.VISIBLE);
        nextButton.setVisibility(content.isLast() ? View.INVISIBLE : View.VISIBLE);
    }

    void onImagesChanged(List<ImageFile> images) {
        this.images = images;
        chapters = Stream.of(images).map(ImageFile::getLinkedChapter).withoutNulls().sortBy(Chapter::getOrder).distinct().toList();

        // Can't access the gallery when there's no page to display
        if (images.size() > 0) binding.viewerGalleryBtn.setVisibility(View.VISIBLE);
        else binding.viewerGalleryBtn.setVisibility(View.GONE);

        maxPageNumber = (int) Stream.of(images).filter(ImageFile::isReadable).count();
    }

    void setDirection(int direction) {
        if (Preferences.Constant.VIEWER_DIRECTION_LTR == direction) {
            pageCurrentNumber = binding.viewerPagerLeftTxt;
            pageMaxNumber = binding.viewerPagerRightTxt;
            binding.pageSlider.setRotationY(0);
            binding.viewerPrevBookBtn.setOnClickListener(v -> previousFunctional());
            binding.viewerNextBookBtn.setOnClickListener(v -> nextFunctional());
        } else if (Preferences.Constant.VIEWER_DIRECTION_RTL == direction) {
            pageCurrentNumber = binding.viewerPagerRightTxt;
            pageMaxNumber = binding.viewerPagerLeftTxt;
            binding.pageSlider.setRotationY(180);
            binding.viewerPrevBookBtn.setOnClickListener(v -> nextFunctional());
            binding.viewerNextBookBtn.setOnClickListener(v -> previousFunctional());
        }

        pageMaxNumber.setOnClickListener(null);
        pageCurrentNumber.setOnClickListener(v -> InputDialog.invokeNumberInputDialog(binding.pageSlider.getContext(), R.string.goto_page, pager::goToPage));
    }

    /**
     * Update the display of page position controls (text and bar)
     */
    void updatePageControls() {
        ImageFile img = pager.getCurrentImg();
        if (null == img) return;

        int pageNum = img.getOrder();
        int pageOffset = 0;
        if (Preferences.isViewerChapteredNavigation()) {
            Chapter currentChap = getCurrentChapter();
            if (currentChap != null) {
                if (null != currentChapter && currentChap.uniqueHash() != currentChapter.uniqueHash())
                    ToastHelper.toast(currentChap.getName());
                List<ImageFile> chapImgs = currentChap.getReadableImageFiles();
                // Caution : ImageFiles inside chapters don't have any displayed order
                // to get it, a mapping between image list and
                if (!chapImgs.isEmpty()) pageOffset = chapImgs.get(0).getOrder();
                pageNum = pageNum - pageOffset + 1;
            }
            currentChapter = currentChap;
        } else {
            currentChapter = null;
        }

        int maxPageNum = maxPageNumber;
        if (Preferences.isViewerChapteredNavigation()) {
            if (currentChapter != null) maxPageNum = currentChapter.getReadableImageFiles().size();
        }

        pageCurrentNumber.setText(String.format(Locale.ENGLISH, "%d", pageNum));
        pageMaxNumber.setText(String.format(Locale.ENGLISH, "%d", maxPageNum));
        superBinding.viewerPagenumberText.setText(String.format(Locale.ENGLISH, "%d / %d", pageNum, maxPageNum));

        int sliderMaxPos = Math.max(1, maxPageNum - 1);
        // Next line to avoid setting a max position inferior to current position
        binding.pageSlider.setValue(Helper.coerceIn(binding.pageSlider.getValue(), 0, sliderMaxPos));
        binding.pageSlider.setValueTo(sliderMaxPos);

        int imageIndex = getCurrentImageIndex();
        if (imageIndex > -1)
            binding.pageSlider.setValue(Helper.coerceIn(imageIndex, 0, sliderMaxPos));
    }

    void previousFunctional() {
        if (!Preferences.isViewerChapteredNavigation()) pager.previousBook();
        else if (!previousChapter()) pager.previousBook();
    }

    void nextFunctional() {
        if (!Preferences.isViewerChapteredNavigation()) pager.nextBook();
        else if (!nextChapter()) pager.nextBook();
    }

    private boolean previousChapter() {
        int currentChIndex = getCurrentChapterIndex();
        if (currentChIndex > 0) {
            Chapter selectedChapter = chapters.get(currentChIndex - 1);
            List<ImageFile> chImgs = selectedChapter.getReadableImageFiles();
            if (chImgs.isEmpty()) return false;
            pager.goToPage(chImgs.get(0).getOrder());
            return true;
        }
        return false;
    }

    private boolean nextChapter() {
        int currentChIndex = getCurrentChapterIndex();
        if (currentChIndex < chapters.size() - 1) {
            Chapter selectedChapter = chapters.get(currentChIndex + 1);
            List<ImageFile> chImgs = selectedChapter.getReadableImageFiles();
            if (chImgs.isEmpty()) return false;
            pager.goToPage(chImgs.get(0).getOrder());
            return true;
        }
        return false;
    }

    private int getCurrentChapterIndex() {
        Chapter ch = getCurrentChapter();
        if (null == ch || null == chapters || chapters.isEmpty()) return -1;

        for (int i = 0; i < chapters.size(); i++)
            if (chapters.get(i).getId() == ch.getId()) return i;

        return -1;
    }

    private Chapter getCurrentChapter() {
        ImageFile startFrom = pager.getCurrentImg();
        if (null == startFrom) return null;

        return startFrom.getLinkedChapter();
    }

    private int getCurrentImageIndex() {
        ImageFile currentImg = pager.getCurrentImg();
        Chapter currentChapter = getCurrentChapter();
        // Absolute index
        if (!Preferences.isViewerChapteredNavigation() || null == currentChapter) {
            return indexAmong(currentImg, images);
        } else { // Relative to current chapter
            return indexAmong(currentImg, currentChapter.getReadableImageFiles());
        }
    }

    private int indexAmong(ImageFile img, List<ImageFile> imgs) {
        if (img != null && imgs != null) {
            for (int i = 0; i < imgs.size(); i++)
                if (imgs.get(i).uniqueHash() == img.uniqueHash()) return i;
        }
        return -1;
    }

    interface Pager {
        void goToPage(int absPageNum);

        void seekToPosition(int absIndex);

        void nextBook();

        void previousBook();

        ImageFile getCurrentImg();
    }
}