package me.devsaki.hentoid.adapters;

import com.annimon.stream.function.Consumer;

import me.devsaki.hentoid.database.domains.Content;

public interface LibraryAdapter {

    Consumer<Content> getOnSourceClickListener();
    Consumer<Content> getOnBookClickListener();
}
