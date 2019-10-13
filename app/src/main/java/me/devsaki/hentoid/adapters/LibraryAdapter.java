package me.devsaki.hentoid.adapters;

import com.annimon.stream.function.Consumer;

import me.devsaki.hentoid.database.domains.Content;

public interface LibraryAdapter {

    long getItemSelectedCount();
    void notifyItemChanged(int pos);

    Consumer<Content> getOnSourceClickListener();
    Consumer<Content> getOpenBookListener();
}
