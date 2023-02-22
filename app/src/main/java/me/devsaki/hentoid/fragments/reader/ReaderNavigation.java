package me.devsaki.hentoid.fragments.reader;

import com.annimon.stream.function.Consumer;

import java.util.List;

import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.databinding.IncludeReaderControlsOverlayBinding;

public class ReaderNavigation {

    private IncludeReaderControlsOverlayBinding binding;

    // == VARS
    private List<ImageFile> images;

    public ReaderNavigation(IncludeReaderControlsOverlayBinding binding) {
        this.binding = binding;
    }

    public void clear() {
        binding = null;
    }

    private void setImageList(List<ImageFile> images) {
        this.images = images;
    }



}
