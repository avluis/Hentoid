package me.devsaki.hentoid.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.adapters.QueueContentAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.services.ContentDownloadService;
import me.devsaki.hentoid.services.QueueManager;
import timber.log.Timber;

/**
 * Created by avluis on 04/10/2016.
 * Presents the list of works currently downloading to the user.
 */
public class QueueFragment extends BaseFragment {

    private List<Content> contents;
    private Context context;
    QueueContentAdapter mAdapter;

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
        } else {
            update();
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

        btnStart.setOnClickListener(v -> {
            if (contents.size() > 0) {
                if (QueueManager.getInstance(context).isQueueEmpty())
                {
                    Intent intent = new Intent(Intent.ACTION_SYNC, null, context, ContentDownloadService.class);
                    context.startService(intent);
                } else {
                    EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_UNPAUSE));
                }
                btnStart.setVisibility(View.GONE);
                btnPause.setVisibility(View.VISIBLE);
                queueText.setText(R.string.queue_dl);
                update();
            }
        });
        btnPause.setOnClickListener(v -> {
            EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE));
            btnPause.setVisibility(View.GONE);
            btnStart.setVisibility(View.VISIBLE);
            queueText.setText(R.string.queue_paused);
            update();
        });

        return rootView;
    }

    private void updatePercent(double percent) {
        if (contents != null && !contents.isEmpty()) {
            contents.get(0).setPercent(percent);
            mAdapter.notifyDataSetChanged();
        }
    }

    public void update() {
        contents = getDB().selectQueueContents();

        // Update list visibility
        if (contents.size() > 0) mEmptyText.setVisibility(View.GONE); else mEmptyText.setVisibility(View.VISIBLE);

        // Update control bar status
        if (contents.size() > 0 && !QueueManager.getInstance(context).isQueueEmpty())
        {
            btnPause.setVisibility(View.VISIBLE);
            btnStart.setVisibility(View.GONE);
            queueText.setText(R.string.queue_dl);
        } else {
            btnPause.setVisibility(View.GONE);

            if (contents.size() > 0) {
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
