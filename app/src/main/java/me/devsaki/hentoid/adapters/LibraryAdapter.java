package me.devsaki.hentoid.adapters;

import androidx.annotation.Nullable;

import com.annimon.stream.function.Consumer;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.IFlexible;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.viewholders.ImageFileFlex;
import me.devsaki.hentoid.viewholders.LibaryItemFlex;

public class LibraryAdapter extends FlexibleAdapter<IFlexible> {
    private final Consumer<Content> onSourceClickListener;

    // TODO instanciate with builder

    public LibraryAdapter(@Nullable List<IFlexible> items, Consumer<Content> onSourceClicked) {
        super(items);
        this.onSourceClickListener = onSourceClicked;
    }

    public Consumer<Content> getOnSourceClickListener() {
        return onSourceClickListener;
    }
}
