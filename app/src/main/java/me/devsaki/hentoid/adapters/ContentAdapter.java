package me.devsaki.hentoid.adapters;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.holders.ContentHolder;
import me.devsaki.hentoid.listener.ItemClickListener;
import me.devsaki.hentoid.listener.ItemLongClickListener;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 04/23/2016.
 * RecyclerView based Content Adapter
 */
public class ContentAdapter extends RecyclerView.Adapter<ContentHolder> {
    private static final String TAG = LogHelper.makeLogTag(ContentAdapter.class);

    private final Context cxt;
    private List<Content> contents = new ArrayList<>();
    private ItemClickListener mClickListener;
    private ItemLongClickListener mLongClickListener;

    public ContentAdapter(Context cxt, final List<Content> contents) {
        this.cxt = cxt;
        this.contents = contents;
    }

    public void setContentList(List<Content> contentList) {
        this.contents = contentList;
        updateContentList();
        // For the first few pages, this should reflect 'Quantity Per Page' setting
        LogHelper.d(TAG, "Number of items in view: " + getItemCount());
    }

    public void updateContentList() {
        this.notifyDataSetChanged();
    }

    @Override
    public ContentHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.row_downloads, parent, false);

        return new ContentHolder(view, mClickListener, mLongClickListener);
    }

    @Override
    public void onBindViewHolder(ContentHolder holder, int position) {
        final Content content = contents.get(position);

        // These two should(?) always match
        LogHelper.d(TAG, "Adapter Position: " + holder.getAdapterPosition());
        // Even if these two are the same, you want to use this one:
        LogHelper.d(TAG, "Layout Position: " + holder.getLayoutPosition());

        String templateTvSeries = cxt.getResources().getString(R.string.tvSeries);
        String templateTvArtist = cxt.getResources().getString(R.string.tvArtists);
        String templateTvTags = cxt.getResources().getString(R.string.tvTags);

        if (content.getTitle() == null) {
            holder.tvTitle.setText(R.string.tvEmpty);
        } else {
            holder.tvTitle.setText(content.getTitle());
            holder.tvTitle.setSelected(true);
        }

        File coverFile = AndroidHelper.getThumb(content, cxt);
        String image = coverFile != null ?
                coverFile.getAbsolutePath() : content.getCoverImageUrl();

        HentoidApplication.getInstance().loadBitmap(image, holder.ivCover);

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
                    AndroidHelper.viewContent(content, cxt);
                }
            });
        } else {
            holder.ivSite.setImageResource(R.drawable.ic_stat_hentoid);
        }

        if (content.getStatus() != null) {
            String status = content.getStatus().getDescription();
            int bg;
            switch (status) {
                case "Downloaded":
                    bg = R.color.card_item_src_normal;
                    break;
                case "Migrated":
                    bg = R.color.card_item_src_migrated;
                    break;
                default:
                    LogHelper.d(TAG, status);
                    bg = R.color.card_item_src_other;
            }
            holder.ivSite.setBackgroundColor(ContextCompat.getColor(cxt, bg));
        } else {
            holder.ivSite.setVisibility(View.GONE);
        }
    }

    @Override
    public long getItemId(int position) {
        return contents.get(position).getId();
    }

    @Override
    public int getItemCount() {
        return (null != contents ? contents.size() : 0);
    }

    public void setOnItemClickListener(ItemClickListener clickListener) {
        this.mClickListener = clickListener;
    }

    public void setOnItemLongClickListener(ItemLongClickListener longClickListener) {
        this.mLongClickListener = longClickListener;
    }
}