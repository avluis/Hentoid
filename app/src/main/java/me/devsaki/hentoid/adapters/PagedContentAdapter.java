package me.devsaki.hentoid.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;

import com.annimon.stream.function.Consumer;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.viewholders.LibraryItem;

public class PagedContentAdapter extends PagedListAdapter<Content, LibraryItem> implements LibraryAdapter {

    private final Consumer<Content> onSourceClickListener;
    private final Consumer<Content> onBookClickListener;

    private PagedContentAdapter(Builder builder) {
        super(DIFF_CALLBACK);
        this.onSourceClickListener = builder.onSourceClickListener;
        this.onBookClickListener = builder.onBookClickListener;
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

        public Builder setSourceClickListener(Consumer<Content> sourceClickListener) {
            this.onSourceClickListener = sourceClickListener;
            return this;
        }

        public Builder setBookClickListener(Consumer<Content> bookClickListener) {
            this.onBookClickListener = bookClickListener;
            return this;
        }

        public PagedContentAdapter build() {
            return new PagedContentAdapter(this);
        }
    }

    public Consumer<Content> getOnSourceClickListener() {
        return onSourceClickListener;
    }

    public Consumer<Content> getOnBookClickListener() {
        return onBookClickListener;
    }
}
