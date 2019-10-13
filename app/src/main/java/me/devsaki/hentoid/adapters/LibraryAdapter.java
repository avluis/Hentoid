package me.devsaki.hentoid.adapters;

import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.LongConsumer;

import me.devsaki.hentoid.database.domains.Content;

public interface LibraryAdapter {

    long getItemSelectedCount();
    void notifyItemChanged(int pos);
    void clearSelection();

    Consumer<Content> getOnSourceClickListener();
    Consumer<Content> getOpenBookListener();
    LongConsumer getSelectionChangedListener();
}
