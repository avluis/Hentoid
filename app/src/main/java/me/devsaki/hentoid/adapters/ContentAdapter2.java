package me.devsaki.hentoid.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.LongConsumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.viewholders.LibraryItem;

public class ContentAdapter2 extends RecyclerView.Adapter<LibraryItem> implements LibraryAdapter {

    private final Consumer<Content> onSourceClickListener;
    private final Consumer<Content> onBookClickListener;
    private final Consumer<Content> onFavClickListener;
    private final Consumer<Content> onErrorClickListener;
    private final LongConsumer onSelectionChangedListener;
    private List<Content> shelf = new ArrayList<>();

    private ContentAdapter2(Builder builder) {
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
        return shelf.get(position).getId();
    }

    public void setShelf(List<Content> shelf) {
        this.shelf = Collections.unmodifiableList(shelf);
    }

    @NonNull
    @Override
    public LibraryItem onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false);
        return new LibraryItem(view, this);
    }

    @Override
    public void onBindViewHolder(@NonNull LibraryItem holder, int position) {
        Content content = shelf.get(position);
        if (content != null) {
            holder.bind(content);
        } else {
            // Null defines a placeholder item - PagedListAdapter automatically
            // invalidates this row when the actual object is loaded from the
            // database.
            holder.clear();
        }
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

        public ContentAdapter2 build() {
            return new ContentAdapter2(this);
        }
    }

    @Override
    public long getItemSelectedCount() {
        return Stream.of(shelf).filter(Content::isSelected).count();
    }

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
}
