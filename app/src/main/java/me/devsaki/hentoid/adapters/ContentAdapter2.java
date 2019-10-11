package me.devsaki.hentoid.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.function.Consumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.viewholders.LibraryItem;

public class ContentAdapter2 extends RecyclerView.Adapter<LibraryItem> implements LibraryAdapter {

    private final Consumer<Content> onSourceClickListener;
    private List<Content> shelf = new ArrayList<>();

    // TODO instanciate with builder
    public ContentAdapter2(Consumer<Content> onSourceClicked) {
        this.onSourceClickListener = onSourceClicked;
    }

    @Override
    public int getItemCount() {
        return shelf.size();
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

    public Consumer<Content> getOnSourceClickListener() {
        return onSourceClickListener;
    }
}
