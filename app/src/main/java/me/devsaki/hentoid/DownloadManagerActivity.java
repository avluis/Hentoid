package me.devsaki.hentoid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.adapters.ContentDownloadManagerAdapter;
import me.devsaki.hentoid.components.HentoidActivity;
import me.devsaki.hentoid.components.HentoidFragment;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.StatusContent;
import me.devsaki.hentoid.service.DownloadManagerService;
import me.devsaki.hentoid.util.NetworkStatus;


public class DownloadManagerActivity extends HentoidActivity<DownloadManagerActivity.DownloadManagerFragment> {

    private static final String TAG = DownloadManagerActivity.class.getName();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                double percent = bundle.getDouble(DownloadManagerService.INTENT_PERCENT_BROADCAST);
                if (percent >= 0) {
                    getFragment().updatePercent(percent);
                } else {
                    getFragment().update();
                }
            }
        }
    };

    @Override
    protected DownloadManagerFragment buildFragment() {
        return new DownloadManagerFragment();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getFragment().update();
        registerReceiver(receiver, new IntentFilter(DownloadManagerService.NOTIFICATION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public static class DownloadManagerFragment extends HentoidFragment {

        private List<Content> contents;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_download_manager, container, false);

            ImageButton btnStart = (ImageButton) rootView.findViewById(R.id.btnStart);
            btnStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getDB().updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                    update();
                    Intent intent = new Intent(Intent.ACTION_SYNC, null, getActivity(), DownloadManagerService.class);
                    getActivity().startService(intent);
                }
            });
            ImageButton btnPause = (ImageButton) rootView.findViewById(R.id.btnPause);
            btnPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getDB().updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);
                    DownloadManagerService.paused = true;
                    update();
                }
            });
            return rootView;
        }

        public void resume(Content content) {
            if(NetworkStatus.getInstance(this.getContext()).isOnline()) {
                content.setStatus(StatusContent.DOWNLOADING);
                getDB().updateContentStatus(content);
                update();
                if (content.getId() == contents.get(0).getId()) {
                    Intent intent = new Intent(Intent.ACTION_SYNC, null, getActivity(), DownloadManagerService.class);
                    getActivity().startService(intent);
                }
            }
        }

        public void pause(Content content) {
            content.setStatus(StatusContent.PAUSED);
            getDB().updateContentStatus(content);
            update();
            if (content.getId() == contents.get(0).getId()) {
                DownloadManagerService.paused = true;
            }
        }

        public void cancel(Content content) {
            content.setStatus(StatusContent.SAVED);
            getDB().updateContentStatus(content);
            if (content.getId() == contents.get(0).getId()) {
                DownloadManagerService.paused = true;
            }
            contents.remove(content);
        }

        public void updatePercent(double percent) {
            if (contents != null && !contents.isEmpty()) {
                contents.get(0).setPercent(percent);

                ((ArrayAdapter) getListAdapter()).notifyDataSetChanged();
            }
        }

        public void update() {
            contents = getDB().selectContentInDownloadManager();
            if (contents == null) {
                contents = new ArrayList<>();
            }
            ContentDownloadManagerAdapter adapter = new ContentDownloadManagerAdapter(getActivity(), contents);
            setListAdapter(adapter);
        }
    }
}
