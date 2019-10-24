package me.devsaki.hentoid.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.LongConsumer;

import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.viewholders.LibraryItem;

/**
 * Adapter for the library screen's endless mode
 *
 * NB : FlexibleAdapter has not been used yet because v5.1.0 does not support PagedList
 *  * We're using instead :
 *  *   - a "classic" RecyclerView.Adapter (for paged mode) <-- ContentAdapter
 *  *   - an PagedListAdapter (for endless mode) <-- current class
 */
public class PagedContentAdapter extends PagedListAdapter<Content, LibraryItem> implements LibraryAdapter {

    // Listeners for holder click events
    private final Consumer<Content> onSourceClickListener;
    private final Consumer<Content> onBookClickListener;
    private final Consumer<Content> onFavClickListener;
    private final Consumer<Content> onErrorClickListener;
    private final LongConsumer onSelectionChangedListener;

    private PagedContentAdapter(Builder builder) {
        super(DIFF_CALLBACK);
        this.onSourceClickListener = builder.onSourceClickListener;
        this.onBookClickListener = builder.onBookClickListener;
        this.onFavClickListener = builder.onFavClickListener;
        this.onErrorClickListener = builder.onErrorClickListener;
        this.onSelectionChangedListener = builder.onSelectionChangedListener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public LibraryItem onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false);
        return new LibraryItem(view, this);
    }

    @Override
    public void onBindViewHolder(@NonNull LibraryItem holder, int position) {
        Content content = getItem(position);
        if (content != null) holder.bind(content);
        else holder.clear();
    }

    @Override
    public long getItemId(int position) {
        Content content = getItem(position);
        if (content != null) return content.getId();
        else return RecyclerView.NO_ID;
    }

    @Override
    public long getSelectedItemsCount() {
        if (getCurrentList() != null)
            return Stream.of(getCurrentList()).filter(Content::isSelected).count(); // TODO doesn't this ruin the performance ?
        else return 0;
    }

    @Override
    public List<Content> getSelectedItems() {
        if (getCurrentList() != null)
            return Stream.of(getCurrentList()).filter(Content::isSelected).toList(); // TODO doesn't this ruin the performance ?
        else return Collections.emptyList();
    }

    /**
     * Unselect all currently selected items
     */
    @Override
    public void clearSelection() {
        for (int i = 0; i < getItemCount(); i++) {
            Content c = getItem(i);
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

    private static DiffUtil.ItemCallback<Content> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Content>() {
                @Override
                public boolean areItemsTheSame(Content oldContent, Content newContent) {
                    return oldContent.getId() == newContent.getId();
                }

                @Override
                public boolean areContentsTheSame(Content oldContent,
                                                  @NonNull Content newContent) {
                    return oldContent.equals(newContent);
                }
            };

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

        public PagedContentAdapter build() {
            return new PagedContentAdapter(this);
        }
    }
}
