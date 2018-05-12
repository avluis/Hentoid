package me.devsaki.hentoid.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
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
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.services.ContentDownloadService;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

/**
 * Created by avluis on 04/10/2016.
 * Presents the list of works currently downloading to the user.
 */
public class QueueFragment extends BaseFragment {

    private Context context;
    private QueueContentAdapter mAdapter;

    // UI ELEMENTS
    private ListView mListView;
    private TextView mEmptyText;
    private ImageButton btnStart;
    private ImageButton btnPause;
    private TextView queueStatus; // 1st line of text displayed on the right of the queue pause / play button
    private TextView queueInfo; // 2nd line of text displayed on the right of the queue pause / play button


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
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_queue, container, false);

        mListView = rootView.findViewById(android.R.id.list);
        mEmptyText = rootView.findViewById(android.R.id.empty);

        btnStart = rootView.findViewById(R.id.btnStart);
        btnPause = rootView.findViewById(R.id.btnPause);
        queueStatus = rootView.findViewById(R.id.queueStatus);
        queueInfo = rootView.findViewById(R.id.queueInfo);

        // Remplace placeholder text by empty strings
        queueStatus.setText(R.string.queue_empty2);
        queueInfo.setText(R.string.queue_empty2);

        btnStart.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_UNPAUSE)));
        btnPause.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE)));

        return rootView;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {

        Timber.d("Event received : %s", event.eventType);

        switch (event.eventType) {
            case DownloadEvent.EV_PROGRESS :
                updateProgress(event.pagesOK, event.pagesKO, event.pagesTotal);
                break;
            case DownloadEvent.EV_UNPAUSE :
                ContentQueueManager.getInstance().unpauseQueue();
                getDB().updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);
                Intent intent = new Intent(Intent.ACTION_SYNC, null, context, ContentDownloadService.class);
                context.startService(intent);
                update(event.eventType);
                break;
            case DownloadEvent.EV_SKIP :
                // Books switch / display handled directly by the adapter
                Content content = mAdapter.getItem(0);
                if (content != null) {
                    updateBookTitle(content.getTitle());
                    queueInfo.setText("");
                }
                break;
            default :
                update(event.eventType);
        }
    }

    private void updateProgress(int pagesOK, int pagesKO, int totalPages) {
        if (!ContentQueueManager.getInstance().isQueuePaused() && mAdapter != null && mAdapter.getCount() > 0) {
            Content content = mAdapter.getItem(0);
            if (content != null) {
                // Update book progress bar
                content.setPercent((pagesOK + pagesKO) * 100.0 / totalPages);
                mAdapter.notifyDataSetChanged();

                // Update information bar
                StringBuilder message = new StringBuilder();
                String processedPagesFmt = Helper.fixedLengthInt(pagesOK, String.valueOf(totalPages).length());
                message.append(processedPagesFmt).append("/").append(totalPages).append(" processed (").append(pagesKO).append(" errors)");

                queueInfo.setText(message.toString());
            }
        }
    }

    private void updateBookTitle(String bookTitle) {
        queueStatus.setText(MessageFormat.format( context.getString(R.string.queue_dl), bookTitle) );
    }

    public void update() { update(-1); }
    public void update(int eventType) {
        List<Content> contents = getDB().selectQueueContents();

        boolean isEmpty = (0 == contents.size());
        boolean isPaused = (!isEmpty && (eventType == DownloadEvent.EV_PAUSE || ContentQueueManager.getInstance().isQueuePaused()) );
        boolean isActive = (!isEmpty && !isPaused);

        Timber.d("Queue state : E/P/A > %s/%s/%s -- %s elements", isEmpty, isPaused, isActive, contents.size());

        // Update list visibility
        mEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        // Update control bar status
        queueInfo.setText(R.string.queue_empty2);

        if (isActive) {
            btnPause.setVisibility(View.VISIBLE);
            btnStart.setVisibility(View.GONE);
            updateBookTitle(contents.get(0).getTitle());
            queueInfo.clearAnimation();
            queueStatus.clearAnimation();
        } else {
            btnPause.setVisibility(View.GONE);

            if (isPaused) {
                btnStart.setVisibility(View.VISIBLE);
                queueStatus.setText(R.string.queue_paused);

                Animation anim = new AlphaAnimation(0.0f, 1.0f);
                anim.setDuration(750);
                anim.setStartOffset(20);
                anim.setRepeatMode(Animation.REVERSE);
                anim.setRepeatCount(Animation.INFINITE);
                queueStatus.startAnimation(anim);
                queueInfo.startAnimation(anim);
            } else {
                btnStart.setVisibility(View.GONE);
                queueStatus.setText(R.string.queue_empty2);
                queueInfo.setText(R.string.queue_empty2);
            }
        }

        // Update adapter
        // TODO - this is kinda shabby
        mAdapter = new QueueContentAdapter(context, contents);
        mListView.setAdapter(mAdapter);
    }

    @Override
    public boolean onBackPressed() {
        // Let the activity handle it.
        return true;
    }
}
