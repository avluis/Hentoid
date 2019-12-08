package me.devsaki.hentoid.adapters;

import android.content.Context;
import android.graphics.PorterDuff;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

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
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.util.ContentHelper;

import static androidx.core.view.ViewCompat.requireViewById;

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
    public View getView(int pos, View rootView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        if (null == container) container = (ListView) parent;
        // Check if an existing view is being reused, otherwise inflate the view
        if (rootView == null) {
            holder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(context);
            rootView = inflater.inflate(R.layout.item_queue, parent, false);

            holder.progressBar = requireViewById(rootView, R.id.pbDownload);
            holder.tvTitle = requireViewById(rootView, R.id.tvTitle);
            holder.ivCover = requireViewById(rootView, R.id.ivCover);
            holder.tvSeries = requireViewById(rootView, R.id.tvSeries);
            holder.tvArtist = requireViewById(rootView, R.id.tvArtist);
            holder.tvPages = requireViewById(rootView, R.id.tvPages);
            holder.tvTags = requireViewById(rootView, R.id.tvTags);
            holder.ivSource = requireViewById(rootView, R.id.ivSite);

            rootView.setTag(holder);
        } else {
            holder = (ViewHolder) rootView.getTag();
        }

        // Populate the data into the template view using the data object
        // Get the data item for this position
        final Content content = getItem(pos);
        if (content != null) {
            populateLayout(holder, content);
            attachButtons(rootView, content, (0 == pos), (getCount() - 1 == pos), getCount());
            updateProgress(holder.progressBar, content, 0 == pos, false);
        }
        // Return the completed view to render on screen
        return rootView;
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
        attachPages(holder);
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
        CharSequence title;
        if (content.getTitle() == null) {
            title = context.getText(R.string.work_untitled);
        } else {
            title = content.getTitle();
        }
        holder.tvTitle.setText(title);
    }

    /**
     * Build the cover layout of the book viewholder using the designated Content properties
     *
     * @param holder  Holder to populate
     * @param content Content to display
     */
    private void attachCover(ViewHolder holder, Content content) {
        String coverFile = ContentHelper.getThumb(content);
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
        String templateSeries = context.getResources().getString(R.string.work_series);
        List<Attribute> seriesAttributes = content.getAttributeMap().get(AttributeType.SERIE);
        if (seriesAttributes == null) {
            holder.tvSeries.setText(templateSeries.replace("@series@", context.getResources().getString(R.string.work_untitled)));
        } else {
            StringBuilder seriesBuilder = new StringBuilder();
            for (int i = 0; i < seriesAttributes.size(); i++) {
                Attribute attribute = seriesAttributes.get(i);
                seriesBuilder.append(attribute.getName());
                if (i != seriesAttributes.size() - 1) {
                    seriesBuilder.append(", ");
                }
            }
            holder.tvSeries.setText(templateSeries.replace("@series@", seriesBuilder));
        }
    }

    /**
     * Build the artist layout of the book viewholder using the designated Content properties
     *
     * @param holder  Holder to populate
     * @param content Content to display
     */
    private void attachArtist(ViewHolder holder, Content content) {
        String templateArtist = context.getResources().getString(R.string.work_artist);
        List<Attribute> attributes = new ArrayList<>();

        List<Attribute> artistAttributes = content.getAttributeMap().get(AttributeType.ARTIST);
        if (artistAttributes != null)
            attributes.addAll(artistAttributes);
        List<Attribute> circleAttributes = content.getAttributeMap().get(AttributeType.CIRCLE);
        if (circleAttributes != null)
            attributes.addAll(circleAttributes);

        if (attributes.isEmpty()) {
            holder.tvArtist.setText(templateArtist.replace("@artist@", context.getResources().getString(R.string.work_untitled)));
        } else {
            List<String> allArtists = new ArrayList<>();
            for (Attribute attribute : attributes) {
                allArtists.add(attribute.getName());
            }
            String artists = android.text.TextUtils.join(", ", allArtists);
            holder.tvArtist.setText(templateArtist.replace("@artist@", artists));
        }
    }

    /**
     * Build the pages layout of the book viewholder using the designated Content properties
     * <p>
     * NB : depending on the source, the number of pages is not always known
     * at the very beginning of the download. To avoid displaying an invalid number and having to
     * refresh it afterwards, queue cards won't have any number of pages displayed
     *
     * @param holder Holder to populate
     */
    private void attachPages(ViewHolder holder) {
        holder.tvPages.setVisibility(View.GONE);
    }

    /**
     * Build the tags layout of the book viewholder using the designated Content properties
     *
     * @param holder  Holder to populate
     * @param content Content to display
     */
    private void attachTags(ViewHolder holder, Content content) {
        List<Attribute> tagsAttributes = content.getAttributeMap().get(AttributeType.TAG);
        if (tagsAttributes == null) {
            holder.tvTags.setText(context.getResources().getString(R.string.work_untitled));
        } else {
            List<String> allTags = new ArrayList<>();
            for (Attribute attribute : tagsAttributes) {
                allTags.add(attribute.getName());
            }
            if (Build.VERSION.SDK_INT >= 24) {
                allTags.sort(null);
            }
            String tags = android.text.TextUtils.join(", ", allTags);
            holder.tvTags.setText(tags);
        }
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
            holder.ivSource.setOnClickListener(v -> ContentHelper.viewContent(context, content));
        } else {
            holder.ivSource.setImageResource(R.drawable.ic_stat_hentoid);
        }
    }

    /**
     * Build the buttons of the book viewholder using the designated Content properties
     *
     * @param rootView    View to populate
     * @param content     Designated content
     * @param isFirstItem True if designated Content is the first item of the queue; false if not
     * @param isLastItem  True if designated Content is the last item of the queue; false if not
     */
    private void attachButtons(@NonNull View rootView, final Content content, boolean isFirstItem, boolean isLastItem, int itemCount) {
        View btnUp = requireViewById(rootView, R.id.queueUpBtn);
        ((ImageView) btnUp).setImageResource(R.drawable.ic_arrow_up);
        btnUp.setVisibility(isFirstItem ? View.INVISIBLE : View.VISIBLE);
        btnUp.setOnClickListener(v -> moveUp(content.getId()));

        View btnTop = requireViewById(rootView, R.id.queueTopBtn);
        ((ImageView) btnTop).setImageResource(R.drawable.ic_doublearrowup);
        btnTop.setVisibility((isFirstItem || itemCount < 3) ? View.INVISIBLE : View.VISIBLE);
        btnTop.setOnClickListener(v -> moveTop(content.getId()));

        View btnDown = requireViewById(rootView, R.id.queueDownBtn);
        ((ImageView) btnDown).setImageResource(R.drawable.ic_arrow_down);
        btnDown.setVisibility(isLastItem ? View.INVISIBLE : View.VISIBLE);
        btnDown.setOnClickListener(v -> moveDown(content.getId()));

        View btnCancel = requireViewById(rootView, R.id.btnCancel);
        btnCancel.setOnClickListener(v -> cancel(content));
    }

    private void updateProgress(@NonNull ProgressBar pb, @NonNull Content content, boolean isFirst, boolean isPausedEvent) {
        boolean isQueueReady = ContentQueueManager.getInstance().isQueueActive() && !ContentQueueManager.getInstance().isQueuePaused() && !isPausedEvent;
        content.computePercent();
        if ((isFirst && isQueueReady) || content.getPercent() > 0) {
            pb.setVisibility(View.VISIBLE);
            if (content.getPercent() > 0) {
                pb.setIndeterminate(false);
                pb.setProgress((int) content.getPercent());

                int color;
                if (isFirst && isQueueReady)
                    color = ContextCompat.getColor(context, R.color.secondary);
                else color = ContextCompat.getColor(context, R.color.medium_gray);
                pb.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
            } else {
                pb.setIndeterminate(true);
            }
        } else {
            pb.setVisibility(View.GONE);
        }
    }

    public void updateProgress(int index, boolean isPausedevent) {
        if (null == container) return;

        View rootView = container.getChildAt(index - container.getFirstVisiblePosition());
        if (rootView == null) return;

        Content content = getItem(index);
        if (null == content) return;

        updateProgress(requireViewById(rootView, R.id.pbDownload), content, 0 == index, isPausedevent);
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
            ContentHelper.removeContent(content);
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
        ProgressBar progressBar;
        TextView tvTitle;
        ImageView ivCover;
        TextView tvSeries;
        TextView tvArtist;
        View tvPages;
        TextView tvTags;
        ImageView ivSource;
    }

    public void dispose() {
        compositeDisposable.clear();
    }
}
