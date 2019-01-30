package me.devsaki.hentoid.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.annimon.stream.function.IntConsumer;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.DownloadsFragment;
import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.listener.ItemClickListener;
import me.devsaki.hentoid.listener.ItemClickListener.ItemSelectListener;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import timber.log.Timber;

/**
 * Created by avluis on 04/23/2016. RecyclerView based Content Adapter
 */
public class ContentAdapter extends RecyclerView.Adapter<ContentHolder> implements ContentListener {

    private static final int VISIBLE_THRESHOLD = 10;

    private final SortedList<Content> mSortedList = new SortedList<>(Content.class, new SortedListCallback(this));
    private final Context context;
    private final ItemSelectListener itemSelectListener;
    private final IntConsumer onContentRemovedListener;
    private final Runnable onContentsClearedListener;
    private final CollectionAccessor collectionAccessor;
    private final int displayMode;
    private final RequestOptions glideRequestOptions;
    private RecyclerView libraryView; // Kept as reference for querying by Content through ID
    private Runnable onScrollToEndListener;
    private Comparator<Content> sortComparator;

    private ContentAdapter(Builder builder) {
        context = builder.context;
        itemSelectListener = builder.itemSelectListener;
        onContentRemovedListener = builder.onContentRemovedListener;
        onContentsClearedListener = builder.onContentsClearedListener;
        collectionAccessor = builder.collectionAccessor;
        sortComparator = builder.sortComparator;
        displayMode = builder.displayMode;
        glideRequestOptions = new RequestOptions()
                .centerInside()
                .error(R.drawable.ic_placeholder);
        setHasStableIds(true);
    }

    public void setSortComparator(Comparator<Content> comparator) {
        sortComparator = comparator;
    }

    public void setOnScrollToEndListener(Runnable listener) {
        this.onScrollToEndListener = listener;
    }

    private void toggleSelection(int pos) {
        Content c = getItemAt(pos);

        if (c != null) {
            c.setSelected(!c.isSelected());
            notifyItemChanged(pos);
        }
    }

    public void clearSelections() {
        for (int i = 0; i < mSortedList.size(); i++) {
            mSortedList.get(i).setSelected(false);
            notifyDataSetChanged();
        }
    }

    private int getSelectedItemsCount() {
        int result = 0;
        for (int i = 0; i < mSortedList.size(); i++) {
            if (mSortedList.get(i).isSelected()) result++;
        }
        return result;
    }

    private List<Content> getSelectedContents() {
        List<Content> selectionList = new ArrayList<>();

        for (int i = 0; i < mSortedList.size(); i++) {
            if (mSortedList.get(i).isSelected()) selectionList.add(mSortedList.get(i));
            Timber.d("Added: %s to list.", mSortedList.get(i).getTitle());
        }

        return selectionList;
    }

    private boolean isSelectedAt(int pos) {
        Content c = getItemAt(pos);
        return null != c && c.isSelected();
    }

    @NonNull
    @Override
    public ContentHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.item_download, parent, false);
        return new ContentHolder(view);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        libraryView = recyclerView;
    }

    @Override
    public void onBindViewHolder(@NonNull ContentHolder holder, final int pos) {
        Content content = mSortedList.get(pos);

        // Initializes the ViewHolder that contains the books
        updateLayoutVisibility(holder, content, pos);
        attachTitle(holder, content);
        attachSeries(holder, content);
        attachArtist(holder, content);
        attachTags(holder, content);
        attachButtons(holder, content, pos);
        attachOnClickListeners(holder, content, pos);
    }

    @Override
    public void onViewRecycled(@NonNull ContentHolder holder) {
        RequestManager requestManager = Glide.with(context.getApplicationContext());
        requestManager.clear(holder.ivCover2);
        requestManager.clear(holder.ivCover);
    }

    private void updateLayoutVisibility(ContentHolder holder, Content content, int pos) {
        if (pos == getItemCount() - VISIBLE_THRESHOLD && onScrollToEndListener != null) {
            onScrollToEndListener.run();
        }

        holder.itemView.setSelected(content.isSelected());

        if (holder.itemView.isSelected()) {
            Timber.d("Position: %s %s is a selected item currently in view.", pos, content.getTitle());

            holder.fullLayout.setVisibility(View.GONE);
            holder.miniLayout.setVisibility(View.VISIBLE);

            Glide.with(context.getApplicationContext())
                    .load(FileHelper.getThumb(content))
                    .apply(glideRequestOptions)
                    .into(holder.ivCover2);
        } else {
            holder.fullLayout.setVisibility(View.VISIBLE);
            holder.miniLayout.setVisibility(View.GONE);

            Glide.with(context.getApplicationContext())
                    .load(FileHelper.getThumb(content))
                    .apply(glideRequestOptions)
                    .into(holder.ivCover);
        }
    }

    private void attachTitle(ContentHolder holder, Content content) {
        CharSequence title;
        if (content.getTitle() == null) {
            title = context.getText(R.string.work_untitled);
        } else {
            title = content.getTitle();
        }

        holder.tvTitle.setText(title);
        if (holder.itemView.isSelected()) {
            holder.tvTitle2.setText(title);
        }

        holder.ivNew.setVisibility((0 == content.getReads()) ? View.VISIBLE : View.GONE);
    }

    private void attachSeries(ContentHolder holder, Content content) {
        String templateSeries = context.getResources().getString(R.string.work_series);
        StringBuilder seriesBuilder = new StringBuilder();
        List<Attribute> seriesAttributes = content.getAttributes().get(AttributeType.SERIE);
        if (seriesAttributes == null) {
            holder.tvSeries.setVisibility(View.GONE);
        } else {
            for (int i = 0; i < seriesAttributes.size(); i++) {
                Attribute attribute = seriesAttributes.get(i);
                seriesBuilder.append(attribute.getName());
                if (i != seriesAttributes.size() - 1) {
                    seriesBuilder.append(", ");
                }
            }
            holder.tvSeries.setVisibility(View.VISIBLE);
        }
        holder.tvSeries.setText(Helper.fromHtml(templateSeries.replace("@series@", seriesBuilder.toString())));

        if (seriesAttributes == null) {
            holder.tvSeries.setText(Helper.fromHtml(templateSeries.replace("@series@",
                    context.getResources().getString(R.string.work_untitled))));
            holder.tvSeries.setVisibility(View.VISIBLE);
        }
    }

    private void attachArtist(ContentHolder holder, Content content) {
        String templateArtist = context.getResources().getString(R.string.work_artist);
        StringBuilder artistsBuilder = new StringBuilder();
        List<Attribute> attributes = new ArrayList<>();
        List<Attribute> artistAttributes = content.getAttributes().get(AttributeType.ARTIST);
        if (artistAttributes != null) attributes.addAll(artistAttributes);
        List<Attribute> circleAttributes = content.getAttributes().get(AttributeType.CIRCLE);
        if (circleAttributes != null) attributes.addAll(circleAttributes);

        if (attributes.isEmpty()) {
            holder.tvArtist.setVisibility(View.GONE);
        } else {
            boolean first = true;
            for (Attribute attribute : attributes) {
                if (first) first = false;
                else artistsBuilder.append(", ");
                artistsBuilder.append(attribute.getName());
            }
            holder.tvArtist.setVisibility(View.VISIBLE);
        }
        holder.tvArtist.setText(Helper.fromHtml(templateArtist.replace("@artist@", artistsBuilder.toString())));

        if (attributes.isEmpty()) {
            holder.tvArtist.setText(Helper.fromHtml(templateArtist.replace("@artist@",
                    context.getResources().getString(R.string.work_untitled))));
            holder.tvArtist.setVisibility(View.VISIBLE);
        }
    }

    private void attachTags(ContentHolder holder, Content content) {
        String templateTags = context.getResources().getString(R.string.work_tags);
        StringBuilder tagsBuilder = new StringBuilder();
        List<Attribute> tagsAttributes = content.getAttributes().get(AttributeType.TAG);
        if (tagsAttributes != null) {
            for (int i = 0; i < tagsAttributes.size(); i++) {
                Attribute attribute = tagsAttributes.get(i);
                if (attribute.getName() != null) {
                    tagsBuilder.append(templateTags.replace("@tag@", attribute.getName()));
                    if (i != tagsAttributes.size() - 1) {
                        tagsBuilder.append(", ");
                    }
                }
            }
        }
        holder.tvTags.setText(Helper.fromHtml(tagsBuilder.toString()));
    }

    private void attachButtons(ContentHolder holder, final Content content, int pos) {
        // Set source icon
        if (content.getSite() != null) {
            int img = content.getSite().getIco();
            holder.ivSite.setImageResource(img);
            holder.ivSite.setOnClickListener(v -> {
                if (getSelectedItemsCount() >= 1) {
                    clearSelections();
                    itemSelectListener.onItemClear(0);
                }
                Helper.viewContent(context, content);
            });
        } else {
            holder.ivSite.setImageResource(R.drawable.ic_stat_hentoid);
        }

        // Set source color
        if (content.getStatus() != null) {
            StatusContent status = content.getStatus();
            holder.ivSite.setBackgroundColor(ContextCompat.getColor(context, R.color.primary));
            holder.ivFavourite.setVisibility((DownloadsFragment.MODE_LIBRARY == displayMode) ? View.VISIBLE : View.GONE);
            holder.ivError.setVisibility((DownloadsFragment.MODE_LIBRARY == displayMode) ? View.VISIBLE : View.GONE);
            holder.ivDownload.setVisibility((DownloadsFragment.MODE_MIKAN == displayMode) ? View.VISIBLE : View.GONE);

            if (DownloadsFragment.MODE_LIBRARY == displayMode) {
                // Favourite toggle
                if (content.isFavourite()) {
                    holder.ivFavourite.setImageResource(R.drawable.ic_fav_full);
                } else {
                    holder.ivFavourite.setImageResource(R.drawable.ic_fav_empty);
                }
                holder.ivFavourite.setOnClickListener(v -> {
                    if (getSelectedItemsCount() >= 1) {
                        clearSelections();
                        itemSelectListener.onItemClear(0);
                    }
                    if (content.isFavourite()) {
                        holder.ivFavourite.setImageResource(R.drawable.ic_fav_empty);
                    } else {
                        holder.ivFavourite.setImageResource(R.drawable.ic_fav_full);
                    }
                    toggleFavourite(content);
                });

                // Error icon
                if (status == StatusContent.ERROR) {
                    holder.ivError.setVisibility(View.VISIBLE);
                    holder.ivError.setOnClickListener(v -> {
                        if (getSelectedItemsCount() >= 1) {
                            clearSelections();
                            itemSelectListener.onItemClear(0);
                        }
                        downloadAgain(content);
                    });
                } else {
                    holder.ivError.setVisibility(View.GONE);
                }
            } else { // Mikan mode

                // "Available online" icon
                if (status == StatusContent.ONLINE) {
                    holder.ivDownload.setImageResource(R.drawable.ic_action_download);
                    holder.ivDownload.setOnClickListener(v -> tryDownloadPages(content));
                }
                // "In queue" icon
                else if (status == StatusContent.DOWNLOADING || status == StatusContent.PAUSED) {
                    holder.ivDownload.setImageResource(R.drawable.ic_action_download);
                    holder.ivDownload.startAnimation(new BlinkAnimation(500, 100));
                    holder.ivDownload.setOnClickListener(v -> Helper.viewQueue(context));
                }
                // "In library" icon
                else if (status == StatusContent.DOWNLOADED || status == StatusContent.MIGRATED || status == StatusContent.ERROR) {
                    holder.ivDownload.setImageResource(R.drawable.ic_action_play);
                    holder.ivDownload.setOnClickListener(v -> FileHelper.openContent(context, content));
                }
            }

        } else {
            holder.ivSite.setVisibility(View.GONE);
        }
    }

    private void tryDownloadPages(Content content) {
        ContentHolder holder = holderByContent(content);
        if (holder != null) {
            holder.ivDownload.startAnimation(new BlinkAnimation(500, 100));
            holder.ivDownload.setOnClickListener(w -> Helper.viewQueue(context));
            collectionAccessor.getPages(content, this);
        }
    }

    private void attachOnClickListeners(final ContentHolder holder, Content content, int pos) {

        // Simple click = open book (library mode only)
        // TODO : implement preview gallery for Mikan mode
        if (DownloadsFragment.MODE_LIBRARY == displayMode) {
            holder.itemView.setOnClickListener(new ItemClickListener(context, content, pos, itemSelectListener) {

                @Override
                public void onClick(View v) {
                    if (getSelectedItemsCount() > 0) { // Selection mode is on
                        int itemPos = holder.getLayoutPosition();
                        toggleSelection(itemPos);
                        setSelected(isSelectedAt(pos), getSelectedItemsCount());
                        onLongClick(v);
                    } else {
                        clearSelections();
                        setSelected(false, 0);

                        super.onClick(v);

                        if (sortComparator.equals(Content.READ_DATE_INV_COMPARATOR)
                                || sortComparator.equals(Content.READS_ORDER_COMPARATOR)
                                || sortComparator.equals(Content.READS_ORDER_INV_COMPARATOR))
                            mSortedList.recalculatePositionOfItemAt(pos); // Reading the book has an effect on its position
                    }
                }
            });
        }

        // Long click = select item (library mode only)
        if (DownloadsFragment.MODE_LIBRARY == displayMode) {
            holder.itemView.setOnLongClickListener(new ItemClickListener(context, content, pos, itemSelectListener) {

                @Override
                public boolean onLongClick(View v) {
                    int itemPos = holder.getLayoutPosition();
                    toggleSelection(itemPos);
                    setSelected(isSelectedAt(pos), getSelectedItemsCount());

                    super.onLongClick(v);

                    return true;
                }
            });
        }
    }

    private void downloadAgain(final Content item) {
        int images;
        int imgErrors = 0;

        images = item.getImageFiles().size();

        for (ImageFile imgFile : item.getImageFiles()) {
            if (imgFile.getStatus() == StatusContent.ERROR) {
                imgErrors++;
            }
        }

        String message = context.getString(R.string.download_again_dialog_message).replace("@clean", images - imgErrors + "").replace("@error", imgErrors + "").replace("@total", images + "");
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.download_again_dialog_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            downloadContent(item);
                            remove(item);
                        })
                .setNegativeButton(android.R.string.no, null)
                .create().show();
    }

    private void downloadContent(Content item) {
        HentoidDB db = HentoidDB.getInstance(context);

        item.setDownloadDate(new Date().getTime());

        if (StatusContent.ONLINE == item.getStatus()) {
            item.setStatus(StatusContent.DOWNLOADING);
            for (ImageFile im : item.getImageFiles()) im.setStatus(StatusContent.SAVED);

            db.insertContent(item);
        } else {
            item.setStatus(StatusContent.DOWNLOADING);
            db.updateContentStatus(item);
        }

        List<Pair<Integer, Integer>> queue = db.selectQueue();
        int lastIndex = 1;
        if (queue.size() > 0) {
            lastIndex = queue.get(queue.size() - 1).second + 1;
        }
        db.insertQueue(item.getId(), lastIndex);

        ContentQueueManager.getInstance().resumeQueue(context);

        ToastUtil.toast(context, R.string.add_to_queue);
    }

    private void shareContent(final Content item) {
        String url = item.getGalleryUrl();

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setDataAndType(Uri.parse(url), "text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, item.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, url);

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_to)));
    }

    private void archiveContent(final Content item) {
        ToastUtil.toast(R.string.packaging_content);
        FileHelper.archiveContent(context, item);
    }

    private void deleteContent(final Content item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.ask_delete)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            clearSelections();
                            deleteItem(item);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> {
                            clearSelections();
                            itemSelectListener.onItemClear(0);
                        })
                .create().show();
    }

    private void deleteContents(final List<Content> items) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.ask_delete_multiple)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            clearSelections();
                            deleteItems(items);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> {
                            clearSelections();
                            itemSelectListener.onItemClear(0);
                        })
                .create().show();
    }

    private void toggleFavourite(Content item) {
        item.setFavourite(!item.isFavourite());

        // Persist in it DB
        final HentoidDB db = HentoidDB.getInstance(context);
        db.updateContentFavourite(item);

        // Persist in it JSON
        String rootFolderName = Preferences.getRootFolderName();
        File dir = new File(rootFolderName, item.getStorageFolder());

        try {
            JsonHelper.saveJson(item, dir);
        } catch (IOException e) {
            Timber.e(e, "Error while writing to " + dir.getAbsolutePath());
        }

    }

    /**
     * Change the state of the item relative to the given content to "downloaded"
     * NB : Specific to Mikan screen
     *
     * @param content content that has been downloaded
     */
    public void switchStateToDownloaded(Content content) {
        ContentHolder holder = holderByContent(content);

        if (holder != null) {
            holder.ivDownload.setImageResource(R.drawable.ic_action_play);
            holder.ivDownload.clearAnimation();
            holder.ivDownload.setOnClickListener(v -> FileHelper.openContent(context, content));
        }
    }

    @Nullable
    private ContentHolder holderByContent(Content content) {
        return (ContentHolder) libraryView.findViewHolderForItemId(content.getId());
    }

    @Override
    public long getItemId(int position) {
        return mSortedList.get(position).getId();
    }

    @Override
    public int getItemCount() {
        return mSortedList.size();
    }

    @Nullable
    private Content getItemAt(int pos) {
        if (mSortedList.size() <= pos) return null;
        else return mSortedList.get(pos);
    }

    public void sharedSelectedItems() {
        int itemCount = getSelectedItemsCount();
        if (itemCount > 0) {
            if (itemCount == 1) {
                Timber.d("Preparing to share selected item...");

                List<Content> items;
                items = getSelectedContents();

                if (!items.isEmpty()) {
                    shareContent(items.get(0));
                } else {
                    itemSelectListener.onItemClear(0);
                    Timber.d("Nothing to share!!");
                }
            } else {
                // TODO: Implement multi-item share
                Timber.d("How even?");
                ToastUtil.toast("Not yet implemented!!");
            }
        } else {
            itemSelectListener.onItemClear(0);
            Timber.d("No items to share!!");
        }
    }

    public void purgeSelectedItems() {
        int itemCount = getSelectedItemsCount();
        if (itemCount > 0) {
            if (itemCount == 1) {
                Timber.d("Preparing to delete selected item...");

                List<Content> items;
                items = getSelectedContents();

                if (!items.isEmpty()) {
                    deleteContent(items.get(0));
                } else {
                    itemSelectListener.onItemClear(0);
                    Timber.d("Nothing to delete!!");
                }
            } else {
                Timber.d("Preparing to delete selected items...");

                List<Content> items;
                items = getSelectedContents();

                if (!items.isEmpty()) {
                    deleteContents(items);
                } else {
                    itemSelectListener.onItemClear(0);
                    Timber.d("No items to delete!!");
                }
            }
        } else {
            itemSelectListener.onItemClear(0);
            Timber.d("No items to delete!!");
        }
    }

    public void archiveSelectedItems() {
        int itemCount = getSelectedItemsCount();
        if (itemCount > 0) {
            if (itemCount == 1) {
                Timber.d("Preparing to archive selected item...");

                List<Content> items;
                items = getSelectedContents();

                if (!items.isEmpty()) {
                    archiveContent(items.get(0));
                } else {
                    itemSelectListener.onItemClear(0);
                    Timber.d("Nothing to archive!!");
                }
            } else {
                // TODO: Implement multi-item archival
                Timber.d("How even?");
                ToastUtil.toast("Not yet implemented!!");
            }
        } else {
            itemSelectListener.onItemClear(0);
            Timber.d("No items to archive!!");
        }
    }

    private void deleteItem(final Content item) {
        remove(item);

        final HentoidDB db = HentoidDB.getInstance(context);
        AsyncTask.execute(() -> {
            FileHelper.removeContent(item);
            db.deleteContent(item);
            Timber.d("Removed item: %s from db and file system.", item.getTitle());
        });

        ToastUtil.toast(context, context.getString(R.string.deleted).replace("@content", item.getTitle()));
    }

    private void deleteItems(final List<Content> contents) {
        mSortedList.beginBatchedUpdates();
        for (Content content : contents) {
            mSortedList.remove(content);
        }
        onContentRemovedListener.accept(contents.size());
        mSortedList.endBatchedUpdates();
        itemSelectListener.onItemClear(0);

        final HentoidDB db = HentoidDB.getInstance(context);

        AsyncTask.execute(() -> {
            for (Content item : contents) {
                FileHelper.removeContent(item);
                db.deleteContent(item);
                Timber.d("Removed item: %s from db and file system.", item.getTitle());
            }
        });

        ToastUtil.toast(context, "Selected items have been deleted.");
    }

    private void remove(Content content) {
        mSortedList.remove(content);
        if (0 == mSortedList.size()) {
            if (onContentsClearedListener != null)
                onContentsClearedListener.run();
        } else {
            if (onContentRemovedListener != null)
                onContentRemovedListener.accept(1);
        }
        if (itemSelectListener != null) itemSelectListener.onItemClear(0);
    }

    public void removeAll() {
        replaceAll(new ArrayList<>());
        onContentsClearedListener.run();
    }

    public void replaceAll(List<Content> contents) {
        mSortedList.beginBatchedUpdates();
        for (int i = mSortedList.size() - 1; i >= 0; i--) {
            final Content content = mSortedList.get(i);
            if (!contents.contains(content)) {
                mSortedList.remove(content);
            } else {
                contents.remove(content);
            }
        }
        mSortedList.addAll(contents);
        mSortedList.endBatchedUpdates();
    }

    public void add(List<Content> contents) {
        mSortedList.beginBatchedUpdates();
        mSortedList.addAll(contents);
        mSortedList.endBatchedUpdates();
    }

    // ContentListener implementation
    @Override
    public void onContentReady(List<Content> results, int totalSelectedContent, int totalContent) { // Listener for pages retrieval in Mikan mode
        if (1 == results.size()) // 1 content with pages
        {
            downloadContent(results.get(0));
        }
    }

    @Override
    public void onContentFailed(Content content, String message) {
        Timber.w(message);
        Snackbar snackbar = Snackbar.make(libraryView, message, Snackbar.LENGTH_LONG);

        if (content != null) {
            ContentHolder holder = holderByContent(content);
            if (holder != null) {
                holder.ivDownload.clearAnimation();
                holder.ivDownload.setOnClickListener(v -> tryDownloadPages(content));
            }
            snackbar.setAction("RETRY", v -> tryDownloadPages(content));
        }
        snackbar.show();
    }

    private class SortedListCallback extends SortedListAdapterCallback<Content> {

        private SortedListCallback(RecyclerView.Adapter adapter) {
            super(adapter);
        }

        @Override
        public int compare(Content a, Content b) {
            return sortComparator.compare(a, b);
        }

        @Override
        public boolean areContentsTheSame(Content oldItem, Content newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areItemsTheSame(Content item1, Content item2) {
            return item1.getId() == item2.getId();
        }
    }

    public static class Builder {
        private Context context;
        private ItemSelectListener itemSelectListener;
        private IntConsumer onContentRemovedListener;
        private Runnable onContentsClearedListener;
        private CollectionAccessor collectionAccessor;
        private Comparator<Content> sortComparator;
        private int displayMode;

        public Builder setContext(Context context) {
            this.context = context;
            return this;
        }

        public Builder setItemSelectListener(ItemSelectListener itemSelectListener) {
            this.itemSelectListener = itemSelectListener;
            return this;
        }

        public Builder setCollectionAccessor(CollectionAccessor collectionAccessor) {
            this.collectionAccessor = collectionAccessor;
            return this;
        }

        public Builder setSortComparator(Comparator<Content> sortComparator) {
            this.sortComparator = sortComparator;
            return this;
        }

        public Builder setDisplayMode(int displayMode) {
            this.displayMode = displayMode;
            return this;
        }

        public Builder setOnContentRemovedListener(IntConsumer onContentRemovedListener) {
            this.onContentRemovedListener = onContentRemovedListener;
            return this;
        }

        public Builder setOnContentsClearedListener(Runnable onContentsClearedListener) {
            this.onContentsClearedListener = onContentsClearedListener;
            return this;
        }

        public ContentAdapter build() {
            return new ContentAdapter(this);
        }
    }
}
