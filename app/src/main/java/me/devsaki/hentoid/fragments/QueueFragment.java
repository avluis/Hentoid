package me.devsaki.hentoid.fragments;

import android.content.Context;
import android.content.Intent;
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

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.adapters.QueueContentAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.services.ContentDownloadService;
import timber.log.Timber;

/**
 * Created by avluis on 04/10/2016.
 * Presents the list of works currently downloading to the user.
 */
public class QueueFragment extends BaseFragment {

    private Context context;
    private QueueContentAdapter mAdapter;
    private boolean isPaused;

    // UI ELEMENTS
    private ListView mListView;
    private TextView mEmptyText;
    private ImageButton btnStart;
    private ImageButton btnPause;
    private TextView queueText; // Text displayed on the right of the queue pause / play button


    public static QueueFragment newInstance() {
        return new QueueFragment();
    }

    @Override
    public void onResume() {
        super.onResume();
        update();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (DownloadEvent.EV_PROGRESS == event.eventType) {
            Double percent = event.percent;
            updatePercent(percent);
        } else if (DownloadEvent.EV_UNPAUSE == event.eventType) {
            getDB().updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);
            Intent intent = new Intent(Intent.ACTION_SYNC, null, context, ContentDownloadService.class);
            context.startService(intent);
            update(event.eventType);
        } else {
            update(event.eventType);
        }
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
        queueText = rootView.findViewById(R.id.queueText);

        btnStart.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_UNPAUSE)));
        btnPause.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE)));

        return rootView;
    }

    private void updatePercent(double percent) {
        if (!isPaused && mAdapter != null && mAdapter.getCount() > 0) {
            Content content = mAdapter.getItem(0);
            if (content != null) {
                content.setPercent(percent);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    public void update() { update (-1); }
    public void update(int eventType) {
        List<Content> contents = getDB().selectQueueContents();

        boolean isEmpty = (0 == contents.size());
        isPaused = (!isEmpty && (eventType == DownloadEvent.EV_PAUSE || contents.get(0).getStatus() == StatusContent.PAUSED));
        boolean isActive = (!isEmpty && !isPaused);

        Timber.d("Queue state : E/P/A > %s/%s/%s", isEmpty, isPaused, isActive);

        // Update list visibility
        mEmptyText.setVisibility(isEmpty?View.VISIBLE:View.GONE);

        // Update control bar status
        if (isActive)
        {
            btnPause.setVisibility(View.VISIBLE);
            btnStart.setVisibility(View.GONE);
            queueText.setText(R.string.queue_dl);
        } else {
            btnPause.setVisibility(View.GONE);

            if (isPaused) {
                btnStart.setVisibility(View.VISIBLE);
                queueText.setText(R.string.queue_paused);
            } else {
                btnStart.setVisibility(View.GONE);
                queueText.setText(R.string.queue_empty2);
            }
        }

        // Update adapter
        mAdapter = new QueueContentAdapter(context, contents);
        mListView.setAdapter(mAdapter);
    }

    @Override
    public boolean onBackPressed() {
        // Let the activity handle it.
        return true;
    }
}
