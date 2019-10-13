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

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.viewholders.LibraryItem;

public class PagedContentAdapter extends PagedListAdapter<Content, LibraryItem> implements LibraryAdapter {

    private final Consumer<Content> onSourceClickListener;
    private final Consumer<Content> onBookClickListener;
    private final LongConsumer onSelectionChangedListener;

    private PagedContentAdapter(Builder builder) {
        super(DIFF_CALLBACK);
        this.onSourceClickListener = builder.onSourceClickListener;
        this.onBookClickListener = builder.onBookClickListener;
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
        if (content != null) {
            holder.bind(content);
        } else {
            // Null defines a placeholder item - PagedListAdapter automatically
            // invalidates this row when the actual object is loaded from the
            // database.
            holder.clear();
        }
    }

    @Override
    public long getItemId(int position) {
        Content content = getItem(position);
        if (content != null) return content.getId();
        else return RecyclerView.NO_ID;
    }


    private static DiffUtil.ItemCallback<Content> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Content>() {
                // Concert details may have changed if reloaded from the database,
                // but ID is fixed.
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
        private LongConsumer onSelectionChangedListener;

        public Builder setSourceClickListener(Consumer<Content> sourceClickListener) {
            this.onSourceClickListener = sourceClickListener;
            return this;
        }

        public Builder setBookClickListener(Consumer<Content> bookClickListener) {
            this.onBookClickListener = bookClickListener;
            return this;
        }

        public Builder setSelectionChangedListener(LongConsumer selectionChangedListener) {
            this.onSelectionChangedListener = selectionChangedListener;
            return this;
        }

        public PagedContentAdapter build() {
            return new PagedContentAdapter(this);
        }
    }

    @Override
    public long getItemSelectedCount() {
        if (getCurrentList() != null)
            return Stream.of(getCurrentList()).filter(Content::isSelected).count();
        else return 0;
    }

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

    public LongConsumer getSelectionChangedListener() {
        return onSelectionChangedListener;
    }
}
