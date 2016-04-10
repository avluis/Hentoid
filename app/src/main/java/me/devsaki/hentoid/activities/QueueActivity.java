package me.devsaki.hentoid.activities;

import android.app.ListFragment;
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

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragmentActivity;
import me.devsaki.hentoid.adapters.QueueContentAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.services.DownloadService;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.util.NetworkStatus;

/**
 * Presents the list of works currently downloading to the user.
 */
public class QueueActivity extends BaseFragmentActivity<QueueActivity.QueueFragment> {
    private static final String TAG = LogHelper.makeLogTag(QueueActivity.class);

    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                double percent = bundle.getDouble(DownloadService.INTENT_PERCENT_BROADCAST);
                if (percent >= 0) {
                    getFragment().updatePercent(percent);
                } else {
                    getFragment().update();
                }
            }
        }
    };
    private ListView mDrawerList;

    @Override
    protected QueueFragment buildFragment() {
        return new QueueFragment();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDrawerList = (ListView) findViewById(R.id.drawer_list);

        super.initializeToolbar();
    }

    @Override
    protected void onResume() {
        super.onResume();

        getFragment().update();
        registerReceiver(receiver, new IntentFilter(DownloadService.DOWNLOAD_NOTIFICATION));
    }

    @Override
    public void onPause() {
        super.onPause();

        unregisterReceiver(receiver);
    }

    public static class QueueFragment extends ListFragment {

        private List<Content> contents;
        private Context mContext;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_queue, container, false);

            mContext = getActivity().getApplicationContext();

            ImageButton btnStart = (ImageButton) rootView.findViewById(R.id.btnStart);
            btnStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getDB().updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                    update();
                    Intent intent = new Intent(Intent.ACTION_SYNC, null, getActivity(),
                            DownloadService.class);
                    getActivity().startService(intent);
                }
            });
            ImageButton btnPause = (ImageButton) rootView.findViewById(R.id.btnPause);
            btnPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getDB().updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);
                    DownloadService.paused = true;
                    update();
                }
            });

            return rootView;
        }

        public void resume(Content content) {
            if (NetworkStatus.isOnline(mContext)) {
                content.setStatus(StatusContent.DOWNLOADING);
                getDB().updateContentStatus(content);
                update();
                if (content.getId() == contents.get(0).getId()) {
                    Intent intent = new Intent(Intent.ACTION_SYNC, null, getActivity(),
                            DownloadService.class);
                    getActivity().startService(intent);
                }
            }
        }

        public void pause(Content content) {
            content.setStatus(StatusContent.PAUSED);
            getDB().updateContentStatus(content);
            update();
            if (content.getId() == contents.get(0).getId()) {
                DownloadService.paused = true;
            }
        }

        public void cancel(Content content) {
            content.setStatus(StatusContent.CANCELED);
            getDB().updateContentStatus(content);
            if (content.getId() == contents.get(0).getId()) {
                int downloadCount = HentoidApplication.getDownloadCount();
                HentoidApplication.setDownloadCount(--downloadCount);
                DownloadService.paused = true;
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
            contents = getDB().selectContentInQueue();
            if (contents == null) {
                contents = new ArrayList<>();
            }
            QueueContentAdapter adapter =
                    new QueueContentAdapter(getActivity(), contents);
            setListAdapter(adapter);
        }
    }
}