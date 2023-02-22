package me.devsaki.hentoid.fragments.reader;

import static java.lang.String.format;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Supplier;

import java.util.List;

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

    // Handlers
    private Consumer<Integer> goToPage;
    private Runnable nextBook;
    private Runnable previousBook;
    private Supplier<ImageFile> getCurrentImg;

    // == VARS
    private List<ImageFile> images;
    private List<Chapter> chapters;
    private int relMaxPageNumber; // Relative (chapter-scale) max page number


    public ReaderNavigation(FragmentReaderPagerBinding binding) {
        this.superBinding = binding;
        this.binding = binding.controlsOverlay;
    }

    void clear() {
        binding = null;
        superBinding = null;
        goToPage = null;
        nextBook = null;
        previousBook = null;
        getCurrentImg = null;
    }

    void setGoToPage(Consumer<Integer> handler) {
        goToPage = handler;
    }

    void setNextBook(Runnable handler) {
        nextBook = handler;
    }

    void setPreviousBook(Runnable handler) {
        previousBook = handler;
    }

    void setGetCurrentImg(Supplier<ImageFile> handler) {
        getCurrentImg = handler;
    }

    void onImagesChanged(List<ImageFile> images) {
        this.images = images;
        chapters = Stream.of(images).map(ImageFile::getLinkedChapter).withoutNulls().sortBy(Chapter::getOrder).toList();

        int sliderMaxPos = Math.max(1, images.size() - 1);
        if (Preferences.isViewerChapteredNavigation()) {
            Chapter currentChapter = getCurrentChapter();
            if (currentChapter != null) {
                List<ImageFile> chImgs = currentChapter.getImageFiles();
                if (chImgs != null) sliderMaxPos = chImgs.size();
            }
        }
        // Next line to avoid setting a max position inferior to current position
        binding.pageSlider.setValue(Helper.coerceIn(binding.pageSlider.getValue(), 0, sliderMaxPos));
        binding.pageSlider.setValueTo(sliderMaxPos);

        // Can't access the gallery when there's no page to display
        if (images.size() > 0)
            binding.viewerGalleryBtn.setVisibility(View.VISIBLE);
        else binding.viewerGalleryBtn.setVisibility(View.GONE);
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
        pageCurrentNumber.setOnClickListener(v -> InputDialog.invokeNumberInputDialog(binding.pageSlider.getContext(), R.string.goto_page, goToPage));
    }

    /**
     * Update the visibility of "next/previous book" buttons
     *
     * @param content Current book
     */
    void updateNavigationUi(@Nonnull Content content) {
        int direction = Preferences.getContentDirection(content.getBookPreferences());
        ImageButton nextButton = (Preferences.Constant.VIEWER_DIRECTION_LTR == direction) ? binding.viewerNextBookBtn : binding.viewerPrevBookBtn;
        ImageButton prevButton = (Preferences.Constant.VIEWER_DIRECTION_LTR == direction) ? binding.viewerPrevBookBtn : binding.viewerNextBookBtn;

        prevButton.setVisibility(content.isFirst() ? View.INVISIBLE : View.VISIBLE);
        nextButton.setVisibility(content.isLast() ? View.INVISIBLE : View.VISIBLE);

        relMaxPageNumber = content.getQtyPages();
        if (Preferences.isViewerChapteredNavigation()) {
            Chapter currentChap = getCurrentChapter();
            if (currentChap != null && currentChap.getImageFiles() != null)
                relMaxPageNumber = currentChap.getImageFiles().size();
        }

        updatePageControls();
    }

    /**
     * Update the display of page position controls (text and bar)
     */
    void updatePageControls() {
        ImageFile img = getCurrentImg.get();
        if (null == img) return;

        int pageNum = img.getOrder();
        if (Preferences.isViewerChapteredNavigation()) {
            Chapter currentChap = getCurrentChapter();
            if (currentChap != null && currentChap.getImageFiles() != null)
                pageNum = pageNum - currentChap.getImageFiles().get(0).getOrder() + 1;
        }

        String pageNumStr = pageNum + "";
        String maxPage = relMaxPageNumber + "";

        pageCurrentNumber.setText(pageNumStr);
        pageMaxNumber.setText(maxPage);
        superBinding.viewerPagenumberText.setText(format("%s / %s", pageNumStr, maxPage));

        binding.pageSlider.setValue(getCurrentImageIndex());
    }

    void previousFunctional() {
        if (!Preferences.isViewerChapteredNavigation()) previousBook.run();
        else previousChapter();
    }

    void nextFunctional() {
        if (!Preferences.isViewerChapteredNavigation()) nextBook.run();
        else nextChapter();
    }

    private void previousChapter() {
        int currentChIndex = getCurrentChapterIndex();
        if (currentChIndex > 0) {
            Chapter selectedChapter = chapters.get(currentChIndex - 1);
            List<ImageFile> chImgs = selectedChapter.getImageFiles();
            if (null == chImgs || chImgs.isEmpty()) return;
            goToPage.accept(chImgs.get(0).getOrder());
            ToastHelper.toast(selectedChapter.getName());
        }
    }

    private void nextChapter() {
        int currentChIndex = getCurrentChapterIndex();
        if (currentChIndex < chapters.size() - 1) {
            Chapter selectedChapter = chapters.get(currentChIndex + 1);
            List<ImageFile> chImgs = selectedChapter.getImageFiles();
            if (null == chImgs || chImgs.isEmpty()) return;
            goToPage.accept(chImgs.get(0).getOrder());
            ToastHelper.toast(selectedChapter.getName());
        }
    }

    private int getCurrentChapterIndex() {
        Chapter ch = getCurrentChapter();
        if (null == ch || null == chapters || chapters.isEmpty()) return -1;

        for (int i = 0; i < chapters.size(); i++)
            if (chapters.get(i).getId() == ch.getId()) return i;

        return -1;
    }

    private Chapter getCurrentChapter() {
        ImageFile startFrom = getCurrentImg.get();
        if (null == startFrom) return null;

        return startFrom.getLinkedChapter();
    }

    private int getCurrentImageIndex() {
        ImageFile currentImg = getCurrentImg.get();
        // Absolute index
        if (!Preferences.isViewerChapteredNavigation()) {
            return indexAmong(currentImg, images);
        } else { // Relative to current chapter
            Chapter currentChapter = getCurrentChapter();
            if (currentChapter != null)
                return indexAmong(currentImg, currentChapter.getImageFiles());
        }
        return -1;
    }

    private int indexAmong(ImageFile img, List<ImageFile> imgs) {
        if (img != null && imgs != null) {
            for (int i = 0; i < imgs.size(); i++)
                if (imgs.get(i).uniqueHash() == img.uniqueHash()) return i;
        }
        return -1;
    }
}
