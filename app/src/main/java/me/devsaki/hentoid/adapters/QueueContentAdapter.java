package me.devsaki.hentoid.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;

/**
 * Created by neko on 11/05/2015.
 * Builds and assigns content from db into adapter for display in queue fragment
 */
public class QueueContentAdapter extends ArrayAdapter<Content> {

    private final Context context;
    private ListView container = null;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    public QueueContentAdapter(Context context, List<Content> contents) {
        super(context, R.layout.item_queue, contents);
        this.context = context;
    }

    @NonNull
    @Override
    public View getView(int pos, View view, @NonNull ViewGroup parent) {
        View v = view;
        ViewHolder holder;
        if (null == container) container = (ListView) parent;
        // Check if an existing view is being reused, otherwise inflate the view
        if (v == null) {
            holder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(context);
            v = inflater.inflate(R.layout.item_queue, parent, false);

            holder.tvTitle = v.findViewById(R.id.tvTitle);
            holder.ivCover = v.findViewById(R.id.ivCover);
            holder.tvSeries = v.findViewById(R.id.tvSeries);
            holder.tvArtist = v.findViewById(R.id.tvArtist);
            holder.tvTags = v.findViewById(R.id.tvTags);
            holder.ivSource = v.findViewById(R.id.ivSource);

            v.setTag(holder);
        } else {
            holder = (ViewHolder) v.getTag();
        }

        // Populate the data into the template view using the data object
        // Get the data item for this position
        final Content content = getItem(pos);
        if (content != null) {
            populateLayout(holder, content);
            attachButtons(v, content, (0 == pos), (getCount() - 1 == pos), getCount());
            updateProgress(v, content);
        }
        // Return the completed view to render on screen
        return v;
    }

    /**
     * Build the entire book layout using the designated Content properties
     *
     * @param holder  Holder to populate
     * @param content Content to display
     */
    private void populateLayout(ViewHolder holder, Content content) {
        attachTitle(holder, content);
        attachCover(holder, content);
        attachSeries(holder, content);
        attachArtist(holder, content);
        attachTags(holder, content);
        attachSource(holder, content);
    }

    /**
     * Build the title layout of the book viewholder using the designated Content properties
     *
     * @param holder  Holder to populate
     * @param content Content to display
     */
    private void attachTitle(ViewHolder holder, Content content) {
        if (content.getTitle() == null) {
            holder.tvTitle.setText(R.string.work_untitled);
        } else {
            holder.tvTitle.setText(content.getTitle());
            holder.tvTitle.setSelected(true);
        }
    }

    /**
     * Build the cover layout of the book viewholder using the designated Content properties
     *
     * @param holder  Holder to populate
     * @param content Content to display
     */
    private void attachCover(ViewHolder holder, Content content) {
        String coverFile = FileHelper.getThumb(content);
        Glide.with(context.getApplicationContext()).clear(holder.ivCover);

        RequestOptions myOptions = new RequestOptions()
                .fitCenter()
                .error(R.drawable.ic_placeholder);

        Glide.with(context.getApplicationContext())
                .load(coverFile)
                .apply(myOptions)
                .into(holder.ivCover);
    }

    /**
     * Build the series layout of the book viewholder using the designated Content properties
     *
     * @param holder  Holder to populate
     * @param content Content to display
     */
    private void attachSeries(ViewHolder holder, Content content) {
        String templateSeries = context.getString(R.string.work_series);
        StringBuilder series = new StringBuilder();
        List<Attribute> seriesAttributes = content.getAttributeMap().get(AttributeType.SERIE);
        if (seriesAttributes == null) {
            holder.tvSeries.setVisibility(View.GONE);
        } else {
            for (int i = 0; i < seriesAttributes.size(); i++) {
                Attribute attribute = seriesAttributes.get(i);
                series.append(attribute.getName());
                if (i != seriesAttributes.size() - 1) {
                    series.append(", ");
                }
            }
            holder.tvSeries.setVisibility(View.VISIBLE);
        }
        holder.tvSeries.setText(Helper.fromHtml(templateSeries.replace("@series@", series)));

        if (seriesAttributes == null) {
            holder.tvSeries.setText(Helper.fromHtml(templateSeries.replace("@series@",
                    context.getResources().getString(R.string.work_untitled))));
            holder.tvSeries.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Build the artist layout of the book viewholder using the designated Content properties
     *
     * @param holder  Holder to populate
     * @param content Content to display
     */
    private void attachArtist(ViewHolder holder, Content content) {
        String templateArtist = context.getString(R.string.work_artist);
        StringBuilder artists = new StringBuilder();
        List<Attribute> attributes = new ArrayList<>();
        List<Attribute> artistAttributes = content.getAttributeMap().get(AttributeType.ARTIST);
        if (artistAttributes != null) attributes.addAll(artistAttributes);
        List<Attribute> circleAttributes = content.getAttributeMap().get(AttributeType.CIRCLE);
        if (circleAttributes != null) attributes.addAll(circleAttributes);

        boolean first = true;
        if (!attributes.isEmpty()) {
            for (Attribute attribute : attributes) {
                if (first) first = false;
                else artists.append(", ");
                artists.append(attribute.getName());
            }
        }
        holder.tvArtist.setText(Helper.fromHtml(templateArtist.replace("@artist@", artists)));

        if (attributes.isEmpty()) {
            holder.tvArtist.setText(Helper.fromHtml(templateArtist.replace("@artist@",
                    context.getResources().getString(R.string.work_untitled))));
            holder.tvArtist.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Build the tags layout of the book viewholder using the designated Content properties
     *
     * @param holder  Holder to populate
     * @param content Content to display
     */
    private void attachTags(ViewHolder holder, Content content) {
        String templateTags = context.getString(R.string.work_tags);
        StringBuilder tags = new StringBuilder();
        List<Attribute> tagsAttributes = content.getAttributeMap().get(AttributeType.TAG);
        if (tagsAttributes != null) {
            for (int i = 0; i < tagsAttributes.size(); i++) {
                Attribute attribute = tagsAttributes.get(i);
                if (attribute.getName() != null) {
                    tags.append(templateTags.replace("@tag@", attribute.getName()));
                    if (i != tagsAttributes.size() - 1) {
                        tags.append(", ");
                    }
                }
            }
        }
        holder.tvTags.setText(Helper.fromHtml(tags.toString()));
    }

    /**
     * Build the source icon layout of the book viewholder using the designated Content properties
     *
     * @param holder  Holder to populate
     * @param content Content to display
     */
    private void attachSource(ViewHolder holder, Content content) {
        if (content.getSite() != null) {
            int img = content.getSite().getIco();
            holder.ivSource.setImageResource(img);
            holder.ivSource.setOnClickListener(v -> Helper.viewContent(context, content));
        } else {
            holder.ivSource.setImageResource(R.drawable.ic_stat_hentoid);
        }
    }

    /**
     * Build the buttons of the book viewholder using the designated Content properties
     *
     * @param view        View to populate
     * @param content     Designated content
     * @param isFirstItem True if designated Content is the first item of the queue; false if not
     * @param isLastItem  True if designated Content is the last item of the queue; false if not
     */
    private void attachButtons(View view, final Content content, boolean isFirstItem, boolean isLastItem, int itemCount) {
        View btnUp = view.findViewById(R.id.queueUpBtn);
        ((ImageView) btnUp).setImageResource(R.drawable.ic_arrow_up);
        btnUp.setVisibility(isFirstItem ? View.INVISIBLE : View.VISIBLE);
        btnUp.setOnClickListener(v -> moveUp(content.getId()));

        View btnTop = view.findViewById(R.id.queueTopBtn);
        ((ImageView) btnTop).setImageResource(R.drawable.ic_doublearrowup);
        btnTop.setVisibility((isFirstItem || itemCount < 3) ? View.INVISIBLE : View.VISIBLE);
        btnTop.setOnClickListener(v -> moveTop(content.getId()));

        View btnDown = view.findViewById(R.id.queueDownBtn);
        ((ImageView) btnDown).setImageResource(R.drawable.ic_arrow_down);
        btnDown.setVisibility(isLastItem ? View.INVISIBLE : View.VISIBLE);
        btnDown.setOnClickListener(v -> moveDown(content.getId()));

        View btnCancel = view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> cancel(content));
    }

    /**
     * Update progress bar according to progress and status of designated Content
     *
     * @param view    Progress bar to use
     * @param content Content whose progress is to be displayed
     */
    private void updateProgress(View view, Content content) {
        ProgressBar pb = view.findViewById(R.id.pbDownload);

        if (content.getStatus() != StatusContent.PAUSED) {
            pb.setVisibility(View.VISIBLE);
            if (content.getPercent() > 0) {
                pb.setIndeterminate(false);
                pb.setProgress((int) content.getPercent());
            } else {
                pb.setIndeterminate(true);
            }
        } else {
            pb.setVisibility(View.INVISIBLE);
        }
    }

    public void updateProgress(int index, Content content) {
        if (null == container) return;

        View view = container.getChildAt(index - container.getFirstVisiblePosition());
        if (view == null) return;

        updateProgress(view, content);
    }

    private void swap(int firstPosition, int secondPosition) {
        Content first = getItem(firstPosition < secondPosition ? firstPosition : secondPosition);
        Content second = getItem(firstPosition < secondPosition ? secondPosition : firstPosition);

        remove(first);
        remove(second);

        insert(first, secondPosition - 1);
        insert(second, firstPosition);
    }

    /**
     * Move designated content up in the download queue (= raise its priority)
     *
     * @param contentId ID of Content whose priority has to be raised
     */
    private void moveUp(long contentId) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        List<QueueRecord> queue = db.selectQueue();

        long prevItemId = 0;
        int prevItemQueuePosition = -1;
        int prevItemPosition = -1;
        int loopPosition = 0;

        setNotifyOnChange(false); // Prevents every update from calling a screen refresh

        for (QueueRecord p : queue) {
            if (p.content.getTargetId() == contentId && prevItemId != 0) {
                db.udpateQueue(p.content.getTargetId(), prevItemQueuePosition);
                db.udpateQueue(prevItemId, p.rank);

                swap(prevItemPosition, loopPosition);
                if (0 == prevItemPosition)
                    EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));
                break;
            } else {
                prevItemId = p.content.getTargetId();
                prevItemQueuePosition = p.rank;
                prevItemPosition = loopPosition;
            }
            loopPosition++;
        }

        notifyDataSetChanged(); // Final screen refresh once everything had been updated
    }

    /**
     * Move designated content on the top of the download queue (= raise its priority)
     *
     * @param contentId ID of Content whose priority has to be raised to the top
     */
    private void moveTop(long contentId) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        List<QueueRecord> queue = db.selectQueue();
        QueueRecord p;

        long topItemId = 0;
        int topItemQueuePosition = -1;

        setNotifyOnChange(false);  // Prevents every update from calling a screen refresh

        for (int i = 0; i < queue.size(); i++) {
            p = queue.get(i);
            if (0 == topItemId) {
                topItemId = p.content.getTargetId();
                topItemQueuePosition = p.rank;
            }

            if (p.content.getTargetId() == contentId) {
                // Put selected item on top of list in the DB
                db.udpateQueue(p.content.getTargetId(), topItemQueuePosition);

                // Update the displayed items
                if (i < getCount()) { // That should never happen, but we do have rare crashes here, so...
                    Content c = getItem(i);
                    remove(c);
                    insert(c, 0);
                }

                // Skip download for the 1st item of the adapter
                EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));

                break;
            } else {
                db.udpateQueue(p.content.getTargetId(), p.rank + 1); // Depriorize every item by 1
            }
        }

        notifyDataSetChanged(); // Final screen refresh once everything had been updated
    }

    /**
     * Move designated content down in the download queue (= lower its priority)
     *
     * @param contentId ID of Content whose priority has to be lowered
     */
    private void moveDown(long contentId) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        List<QueueRecord> queue = db.selectQueue();

        long itemId = 0;
        int itemQueuePosition = -1;
        int itemPosition = -1;
        int loopPosition = 0;

        setNotifyOnChange(false);  // Prevents every update from calling a screen refresh

        for (QueueRecord p : queue) {
            if (p.content.getTargetId() == contentId) {
                itemId = p.content.getTargetId();
                itemQueuePosition = p.rank;
                itemPosition = loopPosition;
            } else if (itemId != 0) {
                db.udpateQueue(p.content.getTargetId(), itemQueuePosition);
                db.udpateQueue(itemId, p.rank);

                swap(itemPosition, loopPosition);

                if (0 == itemPosition)
                    EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));
                break;
            }
            loopPosition++;
        }

        notifyDataSetChanged(); // Final screen refresh once everything had been updated
    }

    /**
     * Cancel download of designated Content
     * NB : Contrary to Pause command, Cancel removes the Content from the download queue
     *
     * @param content Content whose download has to be canceled
     */
    private void cancel(Content content) {
        EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_CANCEL));

        compositeDisposable.add(
                Completable.fromRunnable(() -> doCancel(content.getId()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> super.remove(content))); // Remove the content from the in-memory list and the UI
    }

    private void doCancel(long contentId) {
        // Remove content altogether from the DB (including queue)
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        Content content = db.selectContentById(contentId);
        if (content != null) {
            db.deleteQueue(content);
            db.deleteContent(content);
            // Remove the content from the disk
            FileHelper.removeContent(content);
        }
    }

    public void removeFromQueue(Content content) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        // Remove content from the queue in the DB
        db.deleteQueue(content);
        // Remove the content from the in-memory list and the UI
        super.remove(content);
    }

    // View lookup cache
    private static class ViewHolder {
        TextView tvTitle;
        ImageView ivCover;
        TextView tvSeries;
        TextView tvArtist;
        TextView tvTags;
        ImageView ivSource;
    }

    public void dispose() {
        compositeDisposable.clear();
    }
}
