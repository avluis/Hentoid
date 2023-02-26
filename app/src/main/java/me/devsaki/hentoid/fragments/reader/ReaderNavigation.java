package me.devsaki.hentoid.fragments.reader;

import android.view.View;
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
    private View prevFunctionalButton;
    private View nextFunctionalButton;

    // == VARS
    private final Pager pager;
    private List<ImageFile> images;
    private List<Chapter> chapters;
    private Chapter currentChapter;
    private int maxPageNumber; // Relative (chapter-scale) max page number

    boolean isContentFirst = false;
    boolean isContentLast = false;


    public ReaderNavigation(Pager pager, FragmentReaderPagerBinding inBinding) {
        superBinding = inBinding;
        binding = inBinding.controlsOverlay;
        this.pager = pager;

        binding.pageSlider.addOnChangeListener((slider1, value, fromUser) -> {
            if (fromUser) {
                int offset = 0;
                if (Preferences.isReaderChapteredNavigation()) {
                    Chapter currentChapter = getCurrentChapter();
                    if (currentChapter != null) {
                        List<ImageFile> chapImgs = currentChapter.getReadableImageFiles();
                        if (!chapImgs.isEmpty()) offset = chapImgs.get(0).getOrder() - 1;
                    }
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
        nextFunctionalButton = (Preferences.Constant.VIEWER_DIRECTION_LTR == direction) ? binding.nextBookBtn : binding.prevBookBtn;
        prevFunctionalButton = (Preferences.Constant.VIEWER_DIRECTION_LTR == direction) ? binding.prevBookBtn : binding.nextBookBtn;

        isContentFirst = content.isFirst();
        isContentLast = content.isLast();
    }

    void onImagesChanged(@NonNull List<ImageFile> images) {
        this.images = images;
        chapters = Stream.of(images).map(ImageFile::getLinkedChapter).withoutNulls().sortBy(Chapter::getOrder).distinct().toList();

        // Can't access the gallery when there's no page to display
        if (images.size() > 0) binding.galleryBtn.setVisibility(View.VISIBLE);
        else binding.galleryBtn.setVisibility(View.GONE);

        maxPageNumber = (int) Stream.of(images).filter(ImageFile::isReadable).count();
    }

    private void onChapterChanged(@NonNull Chapter chapter) {
        ToastHelper.toast(chapter.getName());
        updateNextPrevButtonsChapter(chapter);
    }

    private void updateNextPrevButtonsChapter(Chapter chapter) {
        int chapterIndex = getChapterIndex(chapter);
        boolean isChapterFirst = 0 == chapterIndex;
        boolean isChapterLast = chapters.size() - 1 == chapterIndex;
        prevFunctionalButton.setVisibility(isChapterFirst ? View.INVISIBLE : View.VISIBLE);
        nextFunctionalButton.setVisibility(isChapterLast ? View.INVISIBLE : View.VISIBLE);
    }

    void setDirection(int direction) {
        if (Preferences.Constant.VIEWER_DIRECTION_LTR == direction) {
            pageCurrentNumber = binding.pagerLeftTxt;
            pageMaxNumber = binding.pagerRightTxt;
            binding.pageSlider.setRotationY(0);
            binding.prevBookBtn.setOnClickListener(v -> previousFunctional());
            binding.nextBookBtn.setOnClickListener(v -> nextFunctional());
        } else if (Preferences.Constant.VIEWER_DIRECTION_RTL == direction) {
            pageCurrentNumber = binding.pagerRightTxt;
            pageMaxNumber = binding.pagerLeftTxt;
            binding.pageSlider.setRotationY(180);
            binding.prevBookBtn.setOnClickListener(v -> nextFunctional());
            binding.nextBookBtn.setOnClickListener(v -> previousFunctional());
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
        if (Preferences.isReaderChapteredNavigation()) {
            Chapter newChap = getCurrentChapter();
            if (newChap != null) {
                if (null != currentChapter && newChap.uniqueHash() != currentChapter.uniqueHash())
                    onChapterChanged(newChap);
                List<ImageFile> chapImgs = newChap.getReadableImageFiles();
                // Caution : ImageFiles inside chapters don't have any displayed order
                // To get it, a mapping between image list and chapters has to be done
                if (!chapImgs.isEmpty()) pageOffset = chapImgs.get(0).getOrder();
                pageNum = pageNum - pageOffset + 1;
            }
            currentChapter = newChap;
        } else {
            currentChapter = null;
        }

        int maxPageNum = maxPageNumber;
        if (Preferences.isReaderChapteredNavigation()) {
            if (currentChapter != null) maxPageNum = currentChapter.getReadableImageFiles().size();
        }

        pageCurrentNumber.setText(String.format(Locale.ENGLISH, "%d", pageNum));
        pageMaxNumber.setText(String.format(Locale.ENGLISH, "%d", maxPageNum));
        superBinding.viewerPagenumberText.setText(String.format(Locale.ENGLISH, "%d / %d", pageNum, maxPageNum));

        // Only update slider when user isn't skimming _with_ the slider
        if (binding.imagePreviewCenter.getVisibility() != View.VISIBLE) {
            int sliderMaxPos = Math.max(1, maxPageNum - 1);
            // Next line to avoid setting a max position inferior to current position
            binding.pageSlider.setValue(Helper.coerceIn(binding.pageSlider.getValue(), 0, sliderMaxPos));
            binding.pageSlider.setValueTo(sliderMaxPos);

            int imageIndex = getCurrentImageIndex();
            if (imageIndex > -1)
                binding.pageSlider.setValue(Helper.coerceIn(imageIndex, 0, sliderMaxPos));
        }

        if (!Preferences.isReaderChapteredNavigation() || null == chapters || chapters.isEmpty()) {
            prevFunctionalButton.setVisibility(isContentFirst ? View.INVISIBLE : View.VISIBLE);
            nextFunctionalButton.setVisibility(isContentLast ? View.INVISIBLE : View.VISIBLE);
        } else updateNextPrevButtonsChapter(currentChapter);
    }

    void previousFunctional() {
        if (!Preferences.isReaderChapteredNavigation()) pager.previousBook();
        else if (!previousChapter()) pager.previousBook();
    }

    void nextFunctional() {
        if (!Preferences.isReaderChapteredNavigation()) pager.nextBook();
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

    private int getChapterIndex(Chapter ch) {
        if (null == ch || null == chapters || chapters.isEmpty()) return -1;

        for (int i = 0; i < chapters.size(); i++)
            if (chapters.get(i).getId() == ch.getId()) return i;

        return -1;
    }

    private int getCurrentChapterIndex() {
        return getChapterIndex(getCurrentChapter());
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
        if (!Preferences.isReaderChapteredNavigation() || null == currentChapter) {
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