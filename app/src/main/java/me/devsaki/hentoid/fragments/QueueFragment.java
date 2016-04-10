package me.devsaki.hentoid.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.adapters.QueueContentAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.services.DownloadService;

/**
 * Created by avluis on 04/10/2016.
 */
public class QueueFragment extends BaseFragment {

    private ListView mListView;
    private TextView mEmptyText;
    private List<Content> contents;
    private Context mContext;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                double percent = bundle.getDouble(DownloadService.INTENT_PERCENT_BROADCAST);
                if (percent >= 0) {
                    updatePercent(percent);
                } else {
                    update();
                }
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();

        update();
        mContext.registerReceiver(receiver, new IntentFilter(
                DownloadService.DOWNLOAD_NOTIFICATION));
    }

    @Override
    public void onPause() {
        super.onPause();

        mContext.unregisterReceiver(receiver);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_queue, container, false);

        mListView = (ListView) rootView.findViewById(android.R.id.list);
        mEmptyText = (TextView) rootView.findViewById(android.R.id.empty);
        mContext = getActivity().getApplicationContext();

        ImageButton btnStart = (ImageButton) rootView.findViewById(R.id.btnStart);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BaseFragment.getDB().updateContentStatus(StatusContent.DOWNLOADING,
                        StatusContent.PAUSED);
                update();
                Intent intent = new Intent(Intent.ACTION_SYNC, null, mContext,
                        DownloadService.class);
                mContext.startService(intent);
            }
        });
        ImageButton btnPause = (ImageButton) rootView.findViewById(R.id.btnPause);
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BaseFragment.getDB().updateContentStatus(StatusContent.PAUSED,
                        StatusContent.DOWNLOADING);
                DownloadService.paused = true;
                update();
            }
        });

        return rootView;
    }

    public void updatePercent(double percent) {
        if (contents != null && !contents.isEmpty()) {
            contents.get(0).setPercent(percent);
            ((ArrayAdapter) mListView.getAdapter()).notifyDataSetChanged();
        }
    }

    public void update() {
        contents = BaseFragment.getDB().selectContentInQueue();
        if (contents == null) {
            contents = new ArrayList<>();
            mEmptyText.setVisibility(View.VISIBLE);
        }
        QueueContentAdapter adapter = new QueueContentAdapter(mContext, contents);
        mListView.setAdapter(adapter);
    }
}