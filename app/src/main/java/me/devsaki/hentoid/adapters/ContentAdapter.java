package me.devsaki.hentoid.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;
import androidx.recyclerview.widget.SortedListAdapterCallback;

import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.IntConsumer;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.RequestOptions;
import com.crashlytics.android.Crashlytics;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.DownloadsFragment;
import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.listener.ContentClickListener;
import me.devsaki.hentoid.listener.ContentClickListener.ItemSelectListener;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.ContentNotRemovedException;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogUtil;
import me.devsaki.hentoid.util.ToastUtil;
import timber.log.Timber;

/**
 * Created by avluis on 04/23/2016. RecyclerView based Content Adapter
 * TODO - Consider replacing with https://github.com/davideas/FlexibleAdapter
 */
public class ContentAdapter extends RecyclerView.Adapter<ContentHolder> implements PagedResultListener<Content> {

    private static final int VISIBLE_THRESHOLD = 10;

    private final SortedList<Content> mSortedList = new SortedList<>(Content.class, new SortedListCallback(this));
    private final Context context;
    private final ItemSelectListener itemSelectListener;
    private final IntConsumer onContentRemovedListener;
    private final CollectionAccessor collectionAccessor;
    private final int displayMode;
    private final RequestOptions glideRequestOptions;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final Consumer<Content> openBookAction;

    private RecyclerView libraryView; // Kept as reference for querying by Content through ID
    private Runnable onScrollToEndListener;
    private Comparator<Content> sortComparator;

    private ContentAdapter(Builder builder) {
        context = builder.context;
        itemSelectListener = builder.itemSelectListener;
        onContentRemovedListener = builder.onContentRemovedListener;
        collectionAccessor = builder.collectionAccessor;
        sortComparator = builder.sortComparator;
        displayMode = builder.displayMode;
        openBookAction = builder.openBookAction;
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

    /**
     * Initializes the {@link ContentHolder} that contains the books
     */
    @Override
    public void onBindViewHolder(@NonNull ContentHolder holder, final int pos) {
        Content content = mSortedList.get(pos);

        updateLayoutVisibility(holder, content, pos);
        attachTitle(holder, content);
        attachSeries(holder, content);
        attachArtist(holder, content);
        attachTags(holder, content);
        attachButtons(holder, content);
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

        if (content.isBeingDeleted()) {
            BlinkAnimation animation = new BlinkAnimation(500, 250);
            holder.fullLayout.startAnimation(animation);
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
        List<Attribute> seriesAttributes = content.getAttributeMap().get(AttributeType.SERIE);
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
        List<Attribute> artistAttributes = content.getAttributeMap().get(AttributeType.ARTIST);
        if (artistAttributes != null) attributes.addAll(artistAttributes);
        List<Attribute> circleAttributes = content.getAttributeMap().get(AttributeType.CIRCLE);
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
        List<Attribute> tagsAttributes = content.getAttributeMap().get(AttributeType.TAG);
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

    private void attachButtons(ContentHolder holder, final Content content) {
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
            holder.ivDownload.setVisibility((DownloadsFragment.MODE_MIKAN == displayMode) ? View.VISIBLE : View.GONE);

            if (DownloadsFragment.MODE_LIBRARY == displayMode) {
                // Favourite toggle
                if (content.isFavourite()) {
                    holder.ivFavourite.setImageResource(R.drawable.ic_fav_full);
                } else {
                    holder.ivFavourite.setImageResource(R.drawable.ic_fav_empty);
                }
                holder.ivFavourite.setOnClickListener(v -> {
                    if (getSelectedItemsCount() > 0) {
                        clearSelections();
                        itemSelectListener.onItemClear(0);
                    }

                    compositeDisposable.add(
                            Single.fromCallable(() -> toggleFavourite(context, content.getId()))
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                            result -> {
                                                content.setFavourite(result.isFavourite());
                                                if (result.isFavourite()) {
                                                    holder.ivFavourite.setImageResource(R.drawable.ic_fav_full);
                                                } else {
                                                    holder.ivFavourite.setImageResource(R.drawable.ic_fav_empty);
                                                }
                                            },
                                            Timber::e
                                    )
                    );
                });

                // Error icon
                if (status == StatusContent.ERROR) {
                    holder.ivError.setVisibility(View.VISIBLE);
                    holder.ivError.setOnClickListener(v -> {
                        if (getSelectedItemsCount() > 0) {
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
                    holder.ivDownload.setOnClickListener(v -> openBookAction.accept(content));
                }
            }

        } else {
            holder.ivSite.setVisibility(View.GONE);
        }
    }

    // Mikan mode only
    private void tryDownloadPages(Content content) {
        ContentHolder holder = getHolderByContent(content);
        if (holder != null) {
            holder.ivDownload.startAnimation(new BlinkAnimation(500, 100));
            holder.ivDownload.setOnClickListener(w -> Helper.viewQueue(context));
            collectionAccessor.getPages(content, this);
        }
    }

    private void attachOnClickListeners(final ContentHolder holder, Content content, int pos) {

        // Simple click = open book (library mode only)
        if (DownloadsFragment.MODE_LIBRARY == displayMode) {
            holder.itemView.setOnClickListener(new ContentClickListener(content, pos, itemSelectListener) {

                @Override
                public void onClick(View v) {
                    if (getSelectedItemsCount() > 0) { // Selection mode is on
                        int itemPos = holder.getLayoutPosition();
                        if (itemPos > -1) {
                            toggleSelection(itemPos);
                            setSelected(isSelectedAt(pos), getSelectedItemsCount());
                        }
                        onLongClick(v);
                    } else {
                        clearSelections();
                        setSelected(false, 0);
                        openBookAction.accept(content);
                    }
                }
            });
        }

        // Long click = select item (library mode only)
        if (DownloadsFragment.MODE_LIBRARY == displayMode) {
            holder.itemView.setOnLongClickListener(new ContentClickListener(content, pos, itemSelectListener) {

                @Override
                public boolean onLongClick(View v) {
                    int itemPos = holder.getLayoutPosition();
                    if (itemPos > -1) {
                        Content c = getItemAt(itemPos);
                        if (c != null && !c.isBeingDeleted()) {
                            toggleSelection(itemPos);
                            setSelected(isSelectedAt(pos), getSelectedItemsCount());
                        }
                    }

                    super.onLongClick(v);

                    return true;
                }
            });
        }
    }

    private void downloadAgain(final Content item) {
        int images;
        int imgErrors = 0;

        if (item.getImageFiles() != null) {
            images = item.getImageFiles().size();

            for (ImageFile imgFile : item.getImageFiles()) {
                if (imgFile.getStatus() == StatusContent.ERROR) {
                    imgErrors++;
                }
            }

            String message = context.getString(R.string.redownload_dialog_message).replace("@clean", images - imgErrors + "").replace("@error", imgErrors + "").replace("@total", images + "");
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.redownload_dialog_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.yes,
                            (dialog, which) -> {
                                downloadContent(item);
                                remove(item);
                            })
                    .setNegativeButton(android.R.string.no, null)
                    .setNeutralButton(R.string.redownload_view_log,
                            (dialog, which) -> showErrorLog(item))
                    .create().show();
        }
    }

    private void downloadContent(Content item) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);

        if (StatusContent.ONLINE == item.getStatus() && item.getImageFiles() != null)
            for (ImageFile im : item.getImageFiles())
                db.updateImageFileStatusAndParams(im.setStatus(StatusContent.SAVED));

        item.setDownloadDate(new Date().getTime());
        item.setStatus(StatusContent.DOWNLOADING);
        db.insertContent(item);

        List<QueueRecord> queue = db.selectQueue();
        int lastIndex = 1;
        if (!queue.isEmpty()) {
            lastIndex = queue.get(queue.size() - 1).rank + 1;
        }
        db.insertQueue(item.getId(), lastIndex);

        ContentQueueManager.getInstance().resumeQueue(context);

        ToastUtil.toast(context, R.string.add_to_queue);
    }

    private void showErrorLog(final Content content) {
        List<ErrorRecord> errorLog = content.getErrorLog();
        List<String> log = new ArrayList<>();

        LogUtil.LogInfo errorLogInfo = new LogUtil.LogInfo();
        errorLogInfo.logName = "Error";
        errorLogInfo.fileName = "error_log" + content.getId();
        errorLogInfo.noDataMessage = "No error detected.";

        if (errorLog != null) {
            log.add("Error log for " + content.getTitle() + " [" + content.getUniqueSiteId() + "@" + content.getSite().getDescription() + "] : " + errorLog.size() + " errors");
            for (ErrorRecord e : errorLog) log.add(e.toString());
        }

        File logFile = LogUtil.writeLog(context, log, errorLogInfo);
        if (logFile != null) {
            Snackbar snackbar = Snackbar.make(libraryView, R.string.cleanup_done, Snackbar.LENGTH_LONG);
            snackbar.setAction("READ LOG", v -> FileHelper.openFile(context, logFile));
            snackbar.show();
        }
    }

    private void shareContent(final Content item) {
        String url = item.getGalleryUrl();

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, item.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, url);

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_to)));
    }

    private void archiveContent(final Content item) {
        ToastUtil.toast(R.string.packaging_content);
        FileHelper.archiveContent(context, item);
    }

    private void askDeleteItem(final Content item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.ask_delete)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> deleteItem(item))
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> {
                            clearSelections();
                            itemSelectListener.onItemClear(0);
                        })
                .create().show();
    }

    private void askDeleteItems(final List<Content> items) {
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

    private static Content toggleFavourite(Context context, long contentId) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        Content content = db.selectContentById(contentId);

        if (content != null) {
            if (!content.isBeingDeleted()) {
                content.setFavourite(!content.isFavourite());

                // Persist in it DB
                db.insertContent(content);

                // Persist in it JSON
                if (!content.getJsonUri().isEmpty()) FileHelper.updateJson(context, content);
                else FileHelper.createJson(content);
            }
            return content;
        }

        throw new InvalidParameterException("ContentId " + contentId + " does not refer to a valid content");
    }

    /**
     * Change the state of the item relative to the given content to "downloaded"
     * NB : Specific to Mikan screen
     *
     * @param content content that has been downloaded
     */
    public void switchStateToDownloaded(Content content) {
        ContentHolder holder = getHolderByContent(content);

        if (holder != null) {
            holder.ivDownload.setImageResource(R.drawable.ic_action_play);
            holder.ivDownload.clearAnimation();
            holder.ivDownload.setOnClickListener(v -> openBookAction.accept(content));
        }
    }

    @Nullable
    private ContentHolder getHolderByContent(Content content) {
        return (ContentHolder) libraryView.findViewHolderForItemId(content.getId());
    }

    public int getContentPosition(Content content) {
        ContentHolder holder = getHolderByContent(content);
        if (holder != null) return holder.getLayoutPosition();
        else return -1;
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
                    askDeleteItem(items.get(0));
                } else {
                    itemSelectListener.onItemClear(0);
                    Timber.d("Nothing to delete!!");
                }
            } else {
                Timber.d("Preparing to delete selected items...");

                List<Content> items;
                items = getSelectedContents();

                if (!items.isEmpty()) {
                    askDeleteItems(items);
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
        List<Content> list = new ArrayList<>();
        list.add(item);
        deleteItems(list);
    }

    private void deleteItems(final List<Content> contents) {
        // Logging -- TODO remove when "no content found" issue is resolved
        StringBuilder sb = new StringBuilder();
        for (Content c : contents) sb.append(c.getId()).append(",");
        Crashlytics.log("deleteItems " + sb.toString());

        for (Content c : contents) {
            // Flag it to make it unselectable
            c.setIsBeingDeleted(true);
            ObjectBoxDB db = ObjectBoxDB.getInstance(context);
            db.insertContent(c);

            ContentHolder holder = getHolderByContent(c);
            if (holder != null) notifyItemChanged(holder.getAdapterPosition());
        }

        compositeDisposable.add(
                Observable.fromIterable(contents)
                        .subscribeOn(Schedulers.io())
                        .flatMap(s -> Observable.fromCallable(() -> deleteContent(s)))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                this::remove,
                                this::onContentRemoveFail,
                                () -> ToastUtil.toast(context, "Selected items have been deleted.")
                        )
        );
    }

    private Content deleteContent(final Content content) throws ContentNotRemovedException {
        // Check if given content still exists in DB
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        Content theContent = db.selectContentById(content.getId());

        if (theContent != null) {
            FileHelper.removeContent(content);
            db.deleteContent(content);
            Timber.d("Removed item: %s from db and file system.", content.getTitle());
            return content;
        }
        throw new ContentNotRemovedException(content, "ContentId " + content.getId() + " does not refer to a valid content");
    }

    private void onContentRemoveFail(Throwable t) {
        Timber.e(t);
        if (t instanceof ContentNotRemovedException) {
            ContentNotRemovedException e = (ContentNotRemovedException) t;
            Snackbar snackbar = Snackbar.make(libraryView, "Content removal failed", Snackbar.LENGTH_LONG);
            if (e.getContent() != null) {
                // Unflag the item
                e.getContent().setIsBeingDeleted(true);
                ObjectBoxDB db = ObjectBoxDB.getInstance(context);
                db.insertContent(e.getContent());

                ContentHolder holder = getHolderByContent(e.getContent());
                if (holder != null) notifyItemChanged(holder.getAdapterPosition());
                snackbar.setAction("RETRY", v -> deleteItem(e.getContent()));
            }
            snackbar.show();
        }
    }

    private void remove(Content content) {
        mSortedList.remove(content);

        if (onContentRemovedListener != null) onContentRemovedListener.accept(1);
        if (itemSelectListener != null) itemSelectListener.onItemClear(0);
    }

    public void replaceAll(List<Content> contents) {
        mSortedList.beginBatchedUpdates();
        mSortedList.replaceAll(contents);
        mSortedList.endBatchedUpdates();
    }

    public void addAll(List<Content> contents) {
        mSortedList.beginBatchedUpdates();
        mSortedList.addAll(contents);
        mSortedList.endBatchedUpdates();
    }

    // PagedResultListener implementation -- Mikan mode only
    // Listener for pages retrieval (Mikan mode only)
    @Override
    public void onPagedResultReady(List<Content> results, long totalSelectedContent, long totalContent) {
        if (1 == results.size()) // 1 content with pages
        {
            downloadContent(results.get(0));
        }
    }

    // Listener for error visual feedback (Mikan mode only)
    @Override
    public void onPagedResultFailed(Content content, String message) {
        Timber.w(message);
        Snackbar snackbar = Snackbar.make(libraryView, message, Snackbar.LENGTH_LONG);

        if (content != null) {
            ContentHolder holder = getHolderByContent(content);
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
        private CollectionAccessor collectionAccessor;
        private Comparator<Content> sortComparator;
        private int displayMode;
        private Consumer<Content> openBookAction;

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

        public Builder setOpenBookAction(Consumer<Content> action) {
            this.openBookAction = action;
            return this;
        }

        public ContentAdapter build() {
            return new ContentAdapter(this);
        }
    }

    public void dispose() {
        compositeDisposable.clear();
    }
}
