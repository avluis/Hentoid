package me.devsaki.hentoid.adapters;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.holders.ContentHolder;
import me.devsaki.hentoid.listener.ItemClickListener;
import me.devsaki.hentoid.listener.ItemClickListener.ItemSelectListener;
import me.devsaki.hentoid.services.DownloadService;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 04/23/2016.
 * RecyclerView based Content Adapter
 */
public class ContentAdapter extends RecyclerView.Adapter<ContentHolder> {
    private static final String TAG = LogHelper.makeLogTag(ContentAdapter.class);

    private final Context cxt;
    private final SimpleDateFormat sdf;
    private final SparseBooleanArray selectedItems;
    private final ItemSelectListener listener;
    private ContentsWipedListener contentsWipedListener;
    private EndlessScrollListener endlessScrollListener;
    private List<Content> contents = new ArrayList<>();

    public ContentAdapter(Context cxt, final List<Content> contents, ItemSelectListener listener) {
        this.cxt = cxt;
        this.contents = contents;
        this.listener = listener;

        sdf = new SimpleDateFormat("MM/dd/yy HH:mm", Locale.US);
        selectedItems = new SparseBooleanArray();
    }

    public void setEndlessScrollListener(EndlessScrollListener listener) {
        this.endlessScrollListener = listener;
    }

    public void setContentsWipedListener(ContentsWipedListener listener) {
        this.contentsWipedListener = listener;
    }

    private void toggleSelection(int pos) {
        if (selectedItems.get(pos, false)) {
            selectedItems.delete(pos);
            LogHelper.d(TAG, "Removed item: " + pos);
        } else {
            selectedItems.put(pos, true);
            LogHelper.d(TAG, "Added item: " + pos);
        }
        notifyItemChanged(pos);
    }

    public void clearSelections() {
        selectedItems.clear();
        notifyDataSetChanged();
    }

    private int getSelectedItemCount() {
        return selectedItems.size();
    }

    private List<Integer> getSelectedItems() {
        List<Integer> items = new ArrayList<>(selectedItems.size());
        for (int i = 0; i < selectedItems.size(); i++) {
            items.add(selectedItems.keyAt(i));
        }

        return items;
    }

    private boolean getSelectedItem(int item) {
        for (int i = 0; i < selectedItems.size(); i++) {
            if (selectedItems.keyAt(i) == item) {
                return selectedItems.get(item);
            }
        }

        return false;
    }

    public void setContentList(List<Content> contentList) {
        this.contents = contentList;
        updateContentList();
    }

    public void updateContentList() {
        this.notifyDataSetChanged();
    }

    @Override
    public ContentHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.row_downloads, parent, false);

        return new ContentHolder(view);
    }

    @Override
    public void onBindViewHolder(final ContentHolder holder, final int pos) {
        final Content content = contents.get(pos);
        final int VISIBLE_THRESHOLD = 5;

        if (pos == getItemCount() - VISIBLE_THRESHOLD) {
            if (endlessScrollListener != null) {
                endlessScrollListener.onLoadMore(pos);
            }
        }

        if (getSelectedItems() != null) {
            int itemPos = holder.getLayoutPosition();
            boolean selected = getSelectedItem(itemPos);

            if (getSelectedItem(itemPos)) {
                holder.itemView.setSelected(selected);
            } else {
                holder.itemView.setSelected(false);
            }
        }

        final RelativeLayout select = (RelativeLayout) holder.itemView.findViewById(R.id.item);
        LinearLayout actions = (LinearLayout) holder.itemView.findViewById(R.id.item_actions);
        LinearLayout minimal = (LinearLayout) holder.itemView.findViewById(R.id.item_minimal);

        if (holder.itemView.isSelected()) {
            LogHelper.d(TAG, "Position: " + pos + ": " + content.getTitle()
                    + " is a selected item currently in view.");

            if (getSelectedItemCount() > 1) {
                select.setVisibility(View.GONE);
                actions.setVisibility(View.GONE);
                minimal.setVisibility(View.VISIBLE);
            } else {
                select.setVisibility(View.GONE);
                actions.setVisibility(View.VISIBLE);
                minimal.setVisibility(View.GONE);

                holder.tvDate.setText(cxt.getString(R.string.download_date).replace("@date",
                        sdf.format(new Date(content.getDownloadDate()))));
            }
        } else {
            select.setVisibility(View.VISIBLE);
            actions.setVisibility(View.GONE);
            minimal.setVisibility(View.GONE);

            holder.tvDate.setText(R.string.tvEmpty);
        }

        String templateTvSeries = cxt.getResources().getString(R.string.tvSeries);
        String templateTvArtist = cxt.getResources().getString(R.string.tvArtists);
        String templateTvTags = cxt.getResources().getString(R.string.tvTags);

        if (content.getTitle() == null) {
            holder.tvTitle.setText(R.string.tvEmpty);
            if (holder.itemView.isSelected()) {
                holder.tvTitle2.setText(R.string.tvEmpty);
                holder.tvTitle3.setText(R.string.tvEmpty);
            }
        } else {
            holder.tvTitle.setText(content.getTitle());
            holder.tvTitle.setSelected(true);
            if (holder.itemView.isSelected()) {
                holder.tvTitle2.setText(content.getTitle());
                holder.tvTitle2.setSelected(true);
                holder.tvTitle3.setText(content.getTitle());
                holder.tvTitle3.setSelected(true);
            }
        }

        File coverFile = AndroidHelper.getThumb(cxt, content);
        String image = coverFile != null ?
                coverFile.getAbsolutePath() : content.getCoverImageUrl();

        HentoidApp.getInstance().loadBitmap(image, holder.ivCover);

        if (holder.itemView.isSelected()) {
            HentoidApp.getInstance().loadBitmap(image, holder.ivCover2);
            HentoidApp.getInstance().loadBitmap(image, holder.ivCover3);
        }

        String series = "";
        List<Attribute> seriesAttributes = content.getAttributes().get(AttributeType.SERIE);
        if (seriesAttributes == null) {
            holder.tvSeries.setVisibility(View.GONE);
        } else {
            for (int i = 0; i < seriesAttributes.size(); i++) {
                Attribute attribute = seriesAttributes.get(i);
                series += attribute.getName();
                if (i != seriesAttributes.size() - 1) {
                    series += ", ";
                }
            }
            holder.tvSeries.setVisibility(View.VISIBLE);
        }
        holder.tvSeries.setText(Html.fromHtml(templateTvSeries.replace("@series@", series)));

        String artists = "";
        List<Attribute> artistAttributes = content.getAttributes().get(AttributeType.ARTIST);
        if (artistAttributes == null) {
            holder.tvArtist.setVisibility(View.GONE);
        } else {
            for (int i = 0; i < artistAttributes.size(); i++) {
                Attribute attribute = artistAttributes.get(i);
                artists += attribute.getName();
                if (i != artistAttributes.size() - 1) {
                    artists += ", ";
                }
            }
            holder.tvArtist.setVisibility(View.VISIBLE);
        }
        holder.tvArtist.setText(Html.fromHtml(templateTvArtist.replace("@artist@", artists)));

        if (seriesAttributes == null && artistAttributes == null) {
            holder.tvSeries.setText(R.string.tvEmpty);
            holder.tvSeries.setVisibility(View.VISIBLE);
        }

        String tags = "";
        List<Attribute> tagsAttributes = content.getAttributes().get(AttributeType.TAG);
        if (tagsAttributes != null) {
            for (int i = 0; i < tagsAttributes.size(); i++) {
                Attribute attribute = tagsAttributes.get(i);
                if (attribute.getName() != null) {
                    tags += templateTvTags.replace("@tag@", attribute.getName());
                    if (i != tagsAttributes.size() - 1) {
                        tags += ", ";
                    }
                }
            }
        }
        holder.tvTags.setText(Html.fromHtml(tags));

        if (content.getSite() != null) {
            int img = content.getSite().getIco();
            holder.ivSite.setImageResource(img);
            holder.ivSite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getSelectedItemCount() >= 1) {
                        clearSelections();
                        listener.onItemClear(0, -1);
                    }
                    AndroidHelper.viewContent(cxt, content);
                }
            });
        } else {
            holder.ivSite.setImageResource(R.drawable.ic_stat_hentoid);
        }

        if (content.getStatus() != null) {
            StatusContent status = content.getStatus();
            int bg;
            switch (status) {
                case DOWNLOADED:
                    bg = R.color.card_item_src_normal;
                    break;
                case MIGRATED:
                    bg = R.color.card_item_src_migrated;
                    break;
                default:
                    LogHelper.d(TAG, "Position: " + pos + ": " + content.getTitle() +
                            " - Status: " + status);
                    bg = R.color.card_item_src_other;
            }
            holder.ivSite.setBackgroundColor(ContextCompat.getColor(cxt, bg));

            if (status == StatusContent.ERROR) {
                holder.ivError.setVisibility(View.VISIBLE);
                holder.ivError.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (getSelectedItemCount() >= 1) {
                            clearSelections();
                            listener.onItemClear(0, -1);
                        }
                        downloadAgain(content);
                    }
                });
            } else {
                holder.ivError.setVisibility(View.GONE);
            }

        } else {
            holder.ivSite.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(new ItemClickListener(cxt, content, pos, listener) {

            @Override
            public void onClick(View v) {
                if (getSelectedItems() != null) {
                    int itemPos = holder.getLayoutPosition();
                    boolean selected = getSelectedItem(itemPos);
                    boolean selectionMode = getSelectedItemCount() > 0;

                    if (selectionMode) {
                        LogHelper.d(TAG, "In Selection Mode - ignore open requests.");
                        if (selected) {
                            LogHelper.d(TAG, "Item already selected, remove it.");

                            toggleSelection(itemPos);
                            setSelected(false, getSelectedItemCount());
                        } else {
                            LogHelper.d(TAG, "Item not selected, add it.");

                            toggleSelection(itemPos);
                            setSelected(true, getSelectedItemCount());
                        }
                        onLongClick(v);
                    } else {
                        LogHelper.d(TAG, "Not in selection mode, opening item.");

                        clearSelections();
                        setSelected(false, 0);

                        super.onClick(v);
                    }
                }
            }
        });

        holder.itemView.setOnLongClickListener(new ItemClickListener(cxt, content, pos, listener) {

            @Override
            public boolean onLongClick(View v) {
                if (getSelectedItems() != null) {

                    int itemPos = holder.getLayoutPosition();
                    boolean selected = getSelectedItem(itemPos);

                    if (selected) {
                        LogHelper.d(TAG, "Item already selected, remove it.");

                        toggleSelection(itemPos);
                        setSelected(false, getSelectedItemCount());
                    } else {
                        LogHelper.d(TAG, "Item not selected, add it.");

                        toggleSelection(itemPos);
                        setSelected(true, getSelectedItemCount());
                    }

                    super.onLongClick(v);

                    return true;
                }

                return false;
            }
        });

        if (holder.itemView.isSelected()) {
            holder.itemView.findViewById(R.id.delete)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            deleteContent(content);
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

        String message = cxt.getString(R.string.download_again_dialog_message).replace(
                "@error", imgErrors + "").replace("@total", images + "");
        AlertDialog.Builder builder = new AlertDialog.Builder(cxt);
        builder.setTitle(R.string.download_again_dialog_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                HentoidDB db = HentoidDB.getInstance(cxt);

                                item.setStatus(StatusContent.DOWNLOADING);
                                item.setDownloadDate(new Date().getTime());

                                // TODO: Make Asynchronous
                                db.updateContentStatus(item);

                                Intent intent = new Intent(Intent.ACTION_SYNC, null, cxt,
                                        DownloadService.class);
                                cxt.startService(intent);

                                AndroidHelper.toast(cxt, R.string.add_to_queue);
                                removeItem(item);
                                notifyDataSetChanged();
                            }
                        })
                .setNegativeButton(android.R.string.no, null)
                .create().show();
    }

    private void deleteContent(final Content item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(cxt);
        builder.setMessage(R.string.ask_delete)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                clearSelections();
                                deleteItem(item);
                            }
                        })
                .setNegativeButton(android.R.string.no,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                clearSelections();
                                listener.onItemClear(0, -1);
                            }
                        })
                .create().show();
    }

    private void deleteContents(final List<Content> items) {
        AlertDialog.Builder builder = new AlertDialog.Builder(cxt);
        builder.setMessage(R.string.ask_delete_multiple)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                clearSelections();
                                deleteItems(items);
                            }
                        })
                .setNegativeButton(android.R.string.no,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                clearSelections();
                                listener.onItemClear(0, -1);
                            }
                        })
                .create().show();
    }

    @Override
    public long getItemId(int position) {
        return contents.get(position).getId();
    }

    @Override
    public int getItemCount() {
        return (null != contents ? contents.size() : 0);
    }

    public void addItem(int position, Content item) {
        contents.add(position, item);
        notifyItemInserted(position);
    }

    public void purgeSelectedItems() {
        if (getSelectedItemCount() > 0) {
            LogHelper.d(TAG, "Preparing to delete selected items...");

            List<Content> items;
            items = processSelection();

            if (!items.isEmpty()) {
                deleteContents(items);
            } else {
                listener.onItemClear(0, -1);
                LogHelper.d(TAG, "No items to delete!!");
            }
        } else {
            listener.onItemClear(0, -1);
            LogHelper.d(TAG, "No items to delete!!");
        }
    }

    private List<Content> processSelection() {
        List<Content> selectionList = new ArrayList<>();
        List<Integer> selection = getSelectedItems();
        LogHelper.d(TAG, "Selected items: " + selection);

        for (int i = 0; i < selection.size(); i++) {
            selectionList.add(i, contents.get(selection.get(i)));
            LogHelper.d(TAG, "Added: " + contents.get(selection.get(i)).getTitle()
                    + " to removal list.");
        }

        return selectionList;
    }

    private void removeItem(Content item) {
        removeItem(item, true);
    }

    private void removeItem(Content item, boolean broadcast) {
        int position = contents.indexOf(item);
        LogHelper.d(TAG, "Removing item: " + item.getTitle() + " from adapter" + ".");
        contents.remove(position);
        notifyItemRemoved(position);

        if (contents != null) {
            if (contents.size() == 0) {
                contentsWipedListener.onContentsWiped();
            }
            if (broadcast) {
                listener.onItemClear(0, position);
            }
        }
    }

    private void deleteItem(Content item) {
        LogHelper.d(TAG, "Removing item: " + item.getTitle() + " from db and file system" + ".");

        final File dir = AndroidHelper.getContentDownloadDir(cxt, item);
        HentoidDB db = HentoidDB.getInstance(cxt);

        try {
            FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
            LogHelper.e(TAG, "Error deleting directory: ", e);
        }

        // TODO: Make Asynchronous
        db.deleteContent(item);

        removeItem(item);
        notifyDataSetChanged();

        AndroidHelper.toast(cxt, cxt.getString(R.string.deleted).replace("@content",
                item.getTitle()));
    }

    private void deleteItems(List<Content> items) {
        File dir;
        HentoidDB db = HentoidDB.getInstance(cxt);

        for (int i = 0; i < items.size(); i++) {
            removeItem(items.get(i), false);
        }

        for (int i = 0; i < items.size(); i++) {
            dir = AndroidHelper.getContentDownloadDir(cxt, items.get(i));
            LogHelper.d(TAG, "Removing item: " + items.get(i).getTitle()
                    + " from db and file system" + ".");

            try {
                FileUtils.deleteDirectory(dir);
            } catch (IOException e) {
                LogHelper.d(TAG, "Error deleting directory: ", e);
            } finally {
                // TODO: Make Asynchronous
                db.deleteContent(items.get(i));
            }
        }

        listener.onItemClear(0, -1);
        notifyDataSetChanged();

        AndroidHelper.toast(cxt, "Selected items have been deleted.");
    }

    public interface EndlessScrollListener {
        void onLoadMore(int position);
    }

    public interface ContentsWipedListener {
        void onContentsWiped();
    }
}