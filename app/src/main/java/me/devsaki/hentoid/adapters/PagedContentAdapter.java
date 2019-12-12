package me.devsaki.hentoid.adapters;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.AsyncPagedListDiffer;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.LongConsumer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.viewholders.ContentHolder;
import timber.log.Timber;

/**
 * Adapter for the library screen's endless mode
 * <p>
 * NB : FlexibleAdapter has not been used yet because v5.1.0 does not support PagedList
 * * We're using instead :
 * *   - a "classic" RecyclerView.Adapter (for paged mode) <-- ContentAdapter
 * *   - an PagedListAdapter (for endless mode) <-- current class
 */
public class PagedContentAdapter extends PagedListAdapter<Content, ContentHolder> implements LibraryAdapter {

    // Listeners for holder click events
    private final Consumer<Content> onSourceClickListener;
    private final Consumer<Content> onBookClickListener;
    private final Consumer<Content> onFavClickListener;
    private final Consumer<Content> onErrorClickListener;
    private final LongConsumer onSelectionChangedListener;

    private Handler createAsync(Looper looper) {
        if (Build.VERSION.SDK_INT >= 28) return Handler.createAsync(looper);

        try {
            return Handler.class.getDeclaredConstructor(Looper.class, Handler.Callback.class, boolean.class).newInstance(looper, null, true);
        } catch (IllegalAccessException | NoSuchMethodException | InstantiationException | InvocationTargetException e) {
            return new Handler(looper);
        }
    }

    private PagedContentAdapter(Builder builder) {
        super(DIFF_CALLBACK);

        // Fix DiffUtil crash on certain devices - see https://stackoverflow.com/a/56873666/8374722
        try {
            Field mDiffer = PagedListAdapter.class.getDeclaredField("mDiffer");
            Field excecuter = AsyncPagedListDiffer.class.getDeclaredField("mMainThreadExecutor");
            mDiffer.setAccessible(true);
            excecuter.setAccessible(true);

            AsyncPagedListDiffer<?> myDiffer = (AsyncPagedListDiffer<?>) mDiffer.get(this);
            Handler mHandler = createAsync(Looper.getMainLooper());
            Executor foreGround = command -> {
                try {
                    mHandler.post(() -> {
                        try {
                            if (command != null) command.run();
                        } catch (Exception e) {
                            Timber.e(e);
                        }
                    });
                } catch (Exception e) {
                    Timber.e(e);
                }
            };
            excecuter.set(myDiffer, foreGround);
        } catch (Exception e) {
            Timber.e(e);
        }

        this.onSourceClickListener = builder.onSourceClickListener;
        this.onBookClickListener = builder.onBookClickListener;
        this.onFavClickListener = builder.onFavClickListener;
        this.onErrorClickListener = builder.onErrorClickListener;
        this.onSelectionChangedListener = builder.onSelectionChangedListener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ContentHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false);
        return new ContentHolder(view, this);
    }

    @Override
    public void onBindViewHolder(@NonNull ContentHolder holder, int position) {
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
            //noinspection Convert2MethodRef need API24
            return Stream.of(getCurrentList()).filter(c -> c != null).filter(Content::isSelected).count();
        else return 0;
    }

    @Override
    public List<Content> getSelectedItems() {
        if (getCurrentList() != null)
            //noinspection Convert2MethodRef need API24
            return Stream.of(getCurrentList()).filter(c -> c != null).filter(Content::isSelected).toList();
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

    @Nullable
    public Content getItemAtPosition(int pos) {
        return this.getItem(pos);
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
                public boolean areContentsTheSame(@NonNull Content oldContent,
                                                  @NonNull Content newContent) {
                    return oldContent.equals(newContent)
                            && oldContent.getLastReadDate() == newContent.getLastReadDate()
                            && oldContent.isBeingFavourited() == newContent.isBeingFavourited()
                            && oldContent.isFavourite() == newContent.isFavourite();
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
