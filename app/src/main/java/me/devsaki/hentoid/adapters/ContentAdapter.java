package me.devsaki.hentoid.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.LongConsumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.viewholders.ContentHolder;

/**
 * Adapter for the library screen's paged mode
 * <p>
 * NB : FlexibleAdapter has not been used yet because v5.1.0 does not support PagedList
 * We're using instead :
 * - a "classic" RecyclerView.Adapter (for paged mode) <-- current class
 * - an PagedListAdapter (for endless mode) <-- PagedContentAdapter
 */
public class ContentAdapter extends RecyclerView.Adapter<ContentHolder> implements LibraryAdapter {

    // Listeners for holder click events
    private final Consumer<Content> onSourceClickListener;
    private final Consumer<Content> onBookClickListener;
    private final Consumer<Content> onFavClickListener;
    private final Consumer<Content> onErrorClickListener;
    private final LongConsumer onSelectionChangedListener;

    // Currently displayed books
    private List<Content> shelf = new ArrayList<>();

    private ContentAdapter(Builder builder) {
        this.onSourceClickListener = builder.onSourceClickListener;
        this.onBookClickListener = builder.onBookClickListener;
        this.onFavClickListener = builder.onFavClickListener;
        this.onErrorClickListener = builder.onErrorClickListener;
        this.onSelectionChangedListener = builder.onSelectionChangedListener;
        setHasStableIds(true);
    }

    @Override
    public int getItemCount() {
        return shelf.size();
    }

    @Override
    public long getItemId(int position) {
        Content c = shelf.get(position);
        if (c != null) return c.getId();
        else return super.getItemId(position);
    }

    /**
     * Set the list of books to be displayed
     *
     * @param shelf List of books, in their display order
     */
    public void setShelf(@NonNull List<Content> shelf) {
        this.shelf = Collections.unmodifiableList(shelf);
    }

    @NonNull
    @Override
    public ContentHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false);
        return new ContentHolder(view, this);
    }

    @Override
    public void onBindViewHolder(@NonNull ContentHolder holder, int position) {
        Content content = shelf.get(position);
        if (content != null) holder.bind(content);
        else holder.clear();
    }

    @Override
    public long getSelectedItemsCount() {
        //noinspection Convert2MethodRef need API24
        return Stream.of(shelf).filter(c -> c != null).filter(Content::isSelected).count();
    }

    @Override
    public List<Content> getSelectedItems() {
        //noinspection Convert2MethodRef need API24
        return Stream.of(shelf).filter(c -> c != null).filter(Content::isSelected).toList();
    }

    /**
     * Unselect all currently selected items
     */
    @Override
    public void clearSelection() {
        for (int i = 0; i < getItemCount(); i++) {
            Content c = shelf.get(i);
            if (c != null) {
                c.setSelected(false);
                notifyItemChanged(i);
            }
        }
    }

    @Nullable
    public Content getItemAtPosition(int pos) {
        if (pos < 0 || pos > shelf.size() - 1) return null;
        return shelf.get(pos);
    }

    public Consumer<Content> getOnSourceClickListener() {
        return onSourceClickListener;
    }

    public Consumer<Content> getOpenBookListener() {
        return onBookClickListener;
    }

    public Consumer<Content> getFavClickListener() {
        return onFavClickListener;
    }

    public Consumer<Content> getErrorClickListener() {
        return onErrorClickListener;
    }

    public LongConsumer getSelectionChangedListener() {
        return onSelectionChangedListener;
    }

    public static class Builder {
        private Consumer<Content> onSourceClickListener;
        private Consumer<Content> onBookClickListener;
        private Consumer<Content> onFavClickListener;
        private Consumer<Content> onErrorClickListener;
        private LongConsumer onSelectionChangedListener;

        public Builder setSourceClickListener(Consumer<Content> listener) {
            this.onSourceClickListener = listener;
            return this;
        }

        public Builder setBookClickListener(Consumer<Content> listener) {
            this.onBookClickListener = listener;
            return this;
        }

        public Builder setFavClickListener(Consumer<Content> listener) {
            this.onFavClickListener = listener;
            return this;
        }

        public Builder setErrorClickListener(Consumer<Content> listener) {
            this.onErrorClickListener = listener;
            return this;
        }

        public Builder setSelectionChangedListener(LongConsumer listener) {
            this.onSelectionChangedListener = listener;
            return this;
        }

        public ContentAdapter build() {
            return new ContentAdapter(this);
        }
    }
}
