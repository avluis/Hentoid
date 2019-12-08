package me.devsaki.hentoid.adapters;

import androidx.annotation.Nullable;

import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.LongConsumer;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;

/**
 * Common interface for both library screen adapters
 */
public interface LibraryAdapter {

    long getSelectedItemsCount();
    List<Content> getSelectedItems();
    void notifyItemChanged(int pos);
    void clearSelection();
    @Nullable
    Content getItemAtPosition(int pos);

    Consumer<Content> getOnSourceClickListener();
    Consumer<Content> getOpenBookListener();
    Consumer<Content> getFavClickListener();
    Consumer<Content> getErrorClickListener();
    LongConsumer getSelectionChangedListener();
}
