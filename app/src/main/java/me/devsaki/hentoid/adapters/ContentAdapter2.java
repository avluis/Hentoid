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

public class ContentAdapter2 extends PagedListAdapter<Content, LibraryItem> {

    private final Consumer<Content> onSourceClickListener;

    // TODO instanciate with builder
    public ContentAdapter2(Consumer<Content> onSourceClicked) {
        super(DIFF_CALLBACK);
        this.onSourceClickListener = onSourceClicked;
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

    public Consumer<Content> getOnSourceClickListener() {
        return onSourceClickListener;
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
}
