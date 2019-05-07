package me.devsaki.hentoid.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.MessageFormat;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.adapters.QueueContentAdapter;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

/**
 * Created by avluis on 04/10/2016.
 * Presents the list of works currently downloading to the user.
 */
public class QueueFragment extends BaseFragment {

    private Context context;                // App context
    private QueueContentAdapter mAdapter;   // Adapter for queue management

    // UI ELEMENTS
    private TextView mEmptyText;    // "Empty queue" message panel
    private ImageButton btnStart;   // Start / Resume button
    private ImageButton btnPause;   // Pause button
    private ImageButton btnStats;   // Error statistics button
    private TextView queueStatus;   // 1st line of text displayed on the right of the queue pause / play button
    private TextView queueInfo;     // 2nd line of text displayed on the right of the queue pause / play button


    public static QueueFragment newInstance() {
        return new QueueFragment();
    }

    @Override
    public void onResume() {
        super.onResume();
        update();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentActivity activity = getActivity();
        if (null == activity) {
            Timber.e("Activity unreachable !");
            return;
        }
        context = activity.getApplicationContext();
    }

    @Override
    public void onDestroy() {
        mAdapter.dispose();
        super.onDestroy();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_queue, container, false);

        // Book list container
        ListView mListView = rootView.findViewById(android.R.id.list);
        mEmptyText = rootView.findViewById(android.R.id.empty);

        btnStart = rootView.findViewById(R.id.btnStart);
        btnPause = rootView.findViewById(R.id.btnPause);
        btnStats = rootView.findViewById(R.id.btnStats);
        queueStatus = rootView.findViewById(R.id.queueStatus);
        queueInfo = rootView.findViewById(R.id.queueInfo);

        // Remplace placeholder text used in UI designer by empty strings
        queueStatus.setText(R.string.queue_empty2);
        queueInfo.setText(R.string.queue_empty2);

        // Both queue control buttons actually just need to send a signal that will be processed accordingly by whom it may concern
        btnStart.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_UNPAUSE)));
        btnPause.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE)));
        btnStats.setOnClickListener(v -> showStats());

        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        List<Content> contents = db.selectQueueContents();
        mAdapter = new QueueContentAdapter(context, contents);
        mListView.setAdapter(mAdapter);

        return rootView;
    }

    /**
     * Download event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {

        Timber.d("Event received : %s", event.eventType);
        btnStats.setVisibility((event.pagesKO > 0) ? View.VISIBLE : View.GONE);

        switch (event.eventType) {
            case DownloadEvent.EV_PROGRESS:
                updateProgress(event.pagesOK, event.pagesKO, event.pagesTotal);
                break;
            case DownloadEvent.EV_UNPAUSE:
                ContentQueueManager.getInstance().unpauseQueue();
                ObjectBoxDB db = ObjectBoxDB.getInstance(context);
                db.updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);
                ContentQueueManager.getInstance().resumeQueue(context);
                update(event.eventType);
                break;
            case DownloadEvent.EV_SKIP:
                // Books switch / display handled directly by the adapter
                Content content = mAdapter.getItem(0);
                if (content != null) {
                    updateBookTitle(content.getTitle());
                    queueInfo.setText("");
                }
                break;
            case DownloadEvent.EV_COMPLETE:
                mAdapter.removeFromQueue(event.content);
                if (0 == mAdapter.getCount()) btnStats.setVisibility(View.GONE);
                update(event.eventType);
                break;
            default: // EV_PAUSE, EV_CANCEL events + EV_COMPLETE that doesn't have a break
                update(event.eventType);
        }
    }

    /**
     * Update main progress bar and bottom progress panel for current (1st in queue) book
     *
     * @param pagesOK    Number of pages successfully downloaded for current (1st in queue) book
     * @param pagesKO    Number of pages whose download has failed for current (1st in queue) book
     * @param totalPages Total pages of current (1st in queue) book
     */
    private void updateProgress(int pagesOK, int pagesKO, int totalPages) {
        if (!ContentQueueManager.getInstance().isQueuePaused() && mAdapter != null && mAdapter.getCount() > 0) {
            Content content = mAdapter.getItem(0);

            if (content != null) {
                // Pages download has started
                if (pagesKO + pagesOK > 0) {

                    // Update book progress bar
                    content.setPercent((pagesOK + pagesKO) * 100.0 / totalPages);
                    mAdapter.updateProgress(0, content);

                    // Update information bar
                    StringBuilder message = new StringBuilder();
                    String processedPagesFmt = Helper.compensateStringLength(pagesOK, String.valueOf(totalPages).length());
                    message.append(processedPagesFmt).append("/").append(totalPages).append(" processed (").append(pagesKO).append(" errors)");

                    queueInfo.setText(message.toString());
                } else { // Pages download is under preparation
                    queueInfo.setText(R.string.queue_preparing);
                }
            }
        }
    }

    /**
     * Update book title in bottom progress panel
     *
     * @param bookTitle Book title to display
     */
    private void updateBookTitle(String bookTitle) {
        queueStatus.setText(MessageFormat.format(context.getString(R.string.queue_dl), bookTitle));
    }

    public void update() {
        update(-1);
    }

    /**
     * Update the entire Download queue screen
     *
     * @param eventType Event type that triggered the update, if any (See types described in DownloadEvent); -1 if none
     */
    public void update(int eventType) {
        int bookDiff = (eventType == DownloadEvent.EV_CANCEL) ? 1 : 0; // Cancel event means a book will be removed very soon from the queue
        boolean isEmpty = (0 == mAdapter.getCount() - bookDiff);
        boolean isPaused = (!isEmpty && (eventType == DownloadEvent.EV_PAUSE || ContentQueueManager.getInstance().isQueuePaused() || !ContentQueueManager.getInstance().isQueueActive()));
        boolean isActive = (!isEmpty && !isPaused);

        Timber.d("Queue state : E/P/A > %s/%s/%s -- %s elements", isEmpty, isPaused, isActive, mAdapter.getCount());

        // Update list visibility
        mEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        // Update control bar status
        queueInfo.setText(R.string.queue_empty2);

        Content firstContent = isEmpty ? null : mAdapter.getItem(0);

        if (isActive) {
            btnPause.setVisibility(View.VISIBLE);
            btnStart.setVisibility(View.GONE);
            if (firstContent != null) updateBookTitle(firstContent.getTitle());

            // Stop blinking animation, if any
            queueInfo.clearAnimation();
            queueStatus.clearAnimation();
        } else {
            btnPause.setVisibility(View.GONE);

            if (isPaused) {
                btnStart.setVisibility(View.VISIBLE);
                queueStatus.setText(R.string.queue_paused);

                // Set blinking animation when queue is paused
                BlinkAnimation animation = new BlinkAnimation(750, 20);
                queueStatus.startAnimation(animation);
                queueInfo.startAnimation(animation);
            } else { // Empty
                btnStart.setVisibility(View.GONE);
                btnStats.setVisibility(View.GONE);
                queueStatus.setText(R.string.queue_empty2);
                queueInfo.setText(R.string.queue_empty2);
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        // Let the activity handle it.
        return true;
    }

    private void showStats() {
        if (mAdapter != null && mAdapter.getCount() > 0 && mAdapter.getItem(0) != null)
            ErrorStatsDialogFragment.invoke(requireFragmentManager(), mAdapter.getItem(0).getId());
    }
}
