package me.devsaki.hentoid.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
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

/**
 * Created by avluis on 04/10/2016.
 * Presents the list of works currently downloading to the user.
 */
public class QueueFragment extends BaseFragment {

    private ListView mListView;
    private TextView mEmptyText;
    private List<Content> contents;
    private Context context;

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

        context = getActivity().getApplicationContext();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_queue, container, false);

        mListView = rootView.findViewById(android.R.id.list);
        mEmptyText = rootView.findViewById(android.R.id.empty);

        ImageButton btnStart = rootView.findViewById(R.id.btnStart);
        ImageButton btnPause = rootView.findViewById(R.id.btnPause);
        TextView queueText = rootView.findViewById(R.id.queueText);
        btnStart.setOnClickListener(v -> {
            EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_UNPAUSE));
            btnStart.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);
            queueText.setText(R.string.queue_dl);
            update();
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
            ((ArrayAdapter) mListView.getAdapter()).notifyDataSetChanged();
        }
    }

    public void update() {
        contents = getDB().selectQueueContents();
        if (contents == null) {
            contents = Collections.emptyList();
            mEmptyText.setVisibility(View.VISIBLE);
        } else {

            mEmptyText.setVisibility(View.GONE);
        }
        QueueContentAdapter adapter = new QueueContentAdapter(context, contents);
        mListView.setAdapter(adapter);
    }

    @Override
    public boolean onBackPressed() {
        // Let the activity handle it.
        return true;
    }
}
