package me.devsaki.hentoid.adapters;

import androidx.annotation.Nullable;

import com.annimon.stream.function.Consumer;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.IFlexible;
import me.devsaki.hentoid.database.domains.ImageFile;

public class ImageGalleryAdapter extends FlexibleAdapter<IFlexible> {
    private final Consumer<ImageFile> onFavouriteClickListener;

    public ImageGalleryAdapter(@Nullable List<IFlexible> items, Consumer<ImageFile> onFavouriteClickListener) {
        super(items);
        this.onFavouriteClickListener = onFavouriteClickListener;
    }

    public Consumer<ImageFile> getOnFavouriteClickListener() {
        return onFavouriteClickListener;
    }
}
