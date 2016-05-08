package me.devsaki.hentoid.adapters;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
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

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.holders.ContentHolder;
import me.devsaki.hentoid.listener.ItemClickListener;
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
    private List<Content> contents = new ArrayList<>();
    private int selectedItem = -1;

    public ContentAdapter(Context cxt, final List<Content> contents) {
        this.cxt = cxt;
        this.contents = contents;
        sdf = new SimpleDateFormat("MM/dd/yy HH:mm", Locale.US);
    }

    public void setContentList(List<Content> contentList) {
        this.contents = contentList;
        updateContentList();
    }

    public void updateContentList() {
        selectedItem = -1;
        this.notifyDataSetChanged();
    }

    @Override
    public ContentHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.row_downloads, parent, false);

        return new ContentHolder(view);
    }

    @Override
    public void onBindViewHolder(final ContentHolder holder, final int position) {
        final Content content = contents.get(position);

        if (selectedItem != -1) {
            holder.itemView.setSelected(selectedItem == position);
        }

        RelativeLayout select = (RelativeLayout) holder.itemView.findViewById(R.id.item);
        LinearLayout actions = (LinearLayout) holder.itemView.findViewById(R.id.item_actions);

        if (holder.itemView.isSelected()) {
            LogHelper.d(TAG, "Position: " + position + ": " + content.getTitle()
                    + " is a selected item currently in view.");

            select.setVisibility(View.GONE);
            actions.setVisibility(View.VISIBLE);

            holder.tvDate.setText(cxt.getResources().getString(R.string.download_date)
                    .replace("@date", sdf.format(new Date(content.getDownloadDate()))));
        } else {
            select.setVisibility(View.VISIBLE);
            actions.setVisibility(View.GONE);

            holder.tvDate.setText(R.string.tvEmpty);
        }

        String templateTvSeries = cxt.getResources().getString(R.string.tvSeries);
        String templateTvArtist = cxt.getResources().getString(R.string.tvArtists);
        String templateTvTags = cxt.getResources().getString(R.string.tvTags);

        if (content.getTitle() == null) {
            holder.tvTitle.setText(R.string.tvEmpty);
            if (holder.itemView.isSelected()) {
                holder.tvTitle2.setText(R.string.tvEmpty);
            }
        } else {
            holder.tvTitle.setText(content.getTitle());
            holder.tvTitle.setSelected(true);
            if (holder.itemView.isSelected()) {
                holder.tvTitle2.setText(content.getTitle());
                holder.tvTitle2.setSelected(true);
            }
        }

        File coverFile = AndroidHelper.getThumb(cxt, content);
        String image = coverFile != null ?
                coverFile.getAbsolutePath() : content.getCoverImageUrl();

        HentoidApplication.getInstance().loadBitmap(image, holder.ivCover);

        if (holder.itemView.isSelected()) {
            HentoidApplication.getInstance().loadBitmap(image, holder.ivCover2);
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
                    LogHelper.d(TAG, "Position: " + position + ": " + content.getTitle() +
                            " - Status: " + status);
                    bg = R.color.card_item_src_other;
            }
            holder.ivSite.setBackgroundColor(ContextCompat.getColor(cxt, bg));

            if (status == StatusContent.ERROR) {
                holder.ivError.setVisibility(View.VISIBLE);
                holder.ivError.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAgain(content);
                    }
                });
            } else {
                holder.ivError.setVisibility(View.GONE);
            }

        } else {
            holder.ivSite.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(new ItemClickListener(cxt, content, position) {

            @Override
            public void onClick(View v) {
                if (selectedItem != -1) {
                    // If selectedItem is the same as set, unset
                    if (selectedItem == holder.getLayoutPosition()) {
                        notifyItemChanged(selectedItem);
                        holder.itemView.setSelected(false);
                        selectedItem = -1;
                        notifyItemChanged(selectedItem);

                        setSelected(false, -1);

                        super.onClick(v);
                    } else {
                        AndroidHelper.toast(cxt, "Please clear selection first.");
                    }
                } else {
                    setSelected(false, -1);
                    super.onClick(v);
                }
            }
        });

        holder.itemView.setOnLongClickListener(new ItemClickListener(cxt, content, position) {

            @Override
            public boolean onLongClick(View v) {
                // If focusItem is set, ignore
                if (selectedItem != -1) {
                    // If focusItem is the same as set, unset
                    if (holder.getLayoutPosition() == selectedItem) {
                        notifyItemChanged(selectedItem);
                        holder.itemView.setSelected(false);
                        selectedItem = -1;
                        notifyItemChanged(selectedItem);

                        setSelected(false, -1);

                        super.onLongClick(v);

                        return true;
                    } else {
                        int focusedItem = holder.getLayoutPosition();

                        setSelected(true, selectedItem);
                        clearAndSelect(contents, focusedItem);

                        return true;
                    }
                } else if (selectedItem == -1) {
                    // If focusItem is not set, set
                    notifyItemChanged(selectedItem);
                    selectedItem = holder.getLayoutPosition();
                    notifyItemChanged(selectedItem);

                    setSelected(true, selectedItem);

                    super.onLongClick(v);

                    return true;
                }

                return false;
            }
        });

        if (holder.itemView.isSelected()) {
            holder.itemView.findViewById(R.id.ivDelete)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            deleteContent(content, holder);
                        }
                    });
        }
    }

    private void downloadAgain(final Content content) {
        int images;
        int imgErrors = 0;

        images = content.getImageFiles().size();

        for (ImageFile imgFile : content.getImageFiles()) {
            if (imgFile.getStatus() == StatusContent.ERROR) {
                imgErrors++;
            }
        }

        String message = cxt.getString(R.string.download_again_dialog_message).replace("@error",
                imgErrors + "").replace("@total", images + "");
        AlertDialog.Builder builder = new AlertDialog.Builder(cxt);
        builder.setTitle(R.string.download_again_dialog_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                HentoidDB db = HentoidDB.getInstance(cxt);

                                content.setStatus(StatusContent.DOWNLOADING);
                                content.setDownloadDate(new Date().getTime());

                                // TODO: Make Asynchronous
                                db.updateContentStatus(content);

                                Intent intent = new Intent(Intent.ACTION_SYNC, null, cxt,
                                        DownloadService.class);
                                cxt.startService(intent);

                                AndroidHelper.toast(cxt, R.string.add_to_queue);
                                remove(content);
                                notifyDataSetChanged();
                            }
                        })
                .setNegativeButton(android.R.string.no, null)
                .create()
                .show();
    }

    private void deleteContent(final Content content, final ContentHolder holder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(cxt);
        builder.setMessage(R.string.ask_delete)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                notifyItemChanged(selectedItem);
                                holder.itemView.setSelected(false);
                                selectedItem = -1;
                                notifyItemChanged(selectedItem);
                                deleteItem(content);
                            }
                        })
                .setNegativeButton(android.R.string.no,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                notifyItemChanged(selectedItem);
                                holder.itemView.setSelected(false);
                                selectedItem = -1;
                                notifyItemChanged(selectedItem);
                            }
                        })
                .create()
                .show();
    }

    @Override
    public long getItemId(int position) {
        return contents.get(position).getId();
    }

    @Override
    public int getItemCount() {
        return (null != contents ? contents.size() : 0);
    }

    public void add(int position, Content item) {
        contents.add(position, item);
        notifyItemInserted(position);
    }

    private void remove(Content item) {
        int position = contents.indexOf(item);
        LogHelper.d(TAG, "Removing item: " + item.getTitle() + " from adapter" + ".");
        contents.remove(position);
        notifyItemRemoved(position);
    }

    private void deleteItem(Content item) {
        LogHelper.d(TAG, "Removing item: " + item.getTitle() + " from adapter" +
                ", db and file system" + ".");

        final File dir = AndroidHelper.getContentDownloadDir(cxt, item);
        HentoidDB db = HentoidDB.getInstance(cxt);

        try {
            FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
            LogHelper.e(TAG, "Error deleting directory: ", e);
        }

        // TODO: Make Asynchronous
        db.deleteContent(item);

        AndroidHelper.toast(cxt, cxt.getString(R.string.deleted).replace("@content",
                item.getTitle()));

        remove(item);
        notifyDataSetChanged();
    }
}