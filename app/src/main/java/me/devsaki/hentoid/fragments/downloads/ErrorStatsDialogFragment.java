package me.devsaki.hentoid.fragments.downloads;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.LogUtil;

/**
 * Created by Robb on 11/2018
 * Info dialog for download errors details
 */
public class ErrorStatsDialogFragment extends DialogFragment {

    private static String ID = "ID";

    private TextView details;
    private int previousNbErrors;
    private long currentId;
    private View rootView;

    public static void invoke(FragmentManager fragmentManager, long id) {
        ErrorStatsDialogFragment fragment = new ErrorStatsDialogFragment();

        Bundle args = new Bundle();
        args.putLong(ID, id);
        fragment.setArguments(args);

        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Dialog);
        fragment.show(fragmentManager, null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_error_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rootView = view;

        if (getArguments() != null) {
            details = view.findViewById(R.id.stats_details);
            details.setText(R.string.downloads_loading);

            previousNbErrors = 0;
            long id = getArguments().getLong(ID, 0);
            currentId = id;
            if (id > 0) updateStats(id);
        }

        TextView okButton = view.findViewById(R.id.stats_ok);
        okButton.setOnClickListener(v -> this.dismiss());

        TextView logButton = view.findViewById(R.id.stats_log);
        logButton.setOnClickListener(v -> this.showErrorLog());
    }

    private void updateStats(long contentId) {
        List<ErrorRecord> errors = ObjectBoxDB.getInstance(getContext()).selectErrorRecordByContentId(contentId);
        Map<ErrorType, Integer> errorsByType = new HashMap<>();

        for (ErrorRecord error : errors) {
            if (errorsByType.containsKey(error.type)) {
                int nbErrors = errorsByType.get(error.type);
                errorsByType.put(error.type, ++nbErrors);
            } else {
                errorsByType.put(error.type, 1);
            }
        }

        StringBuilder detailsStr = new StringBuilder();

        for (ErrorType type : errorsByType.keySet()) {
            detailsStr.append(type.getName()).append(" : ");
            detailsStr.append(errorsByType.get(type));
            detailsStr.append(System.getProperty("line.separator"));
        }

        details.setText(detailsStr.toString());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (event.eventType == DownloadEvent.EV_COMPLETE) {
            details.setText("Download complete");
            previousNbErrors = 0;
        } else if (event.eventType == DownloadEvent.EV_CANCEL) {
            details.setText("Download cancelled");
            previousNbErrors = 0;
        } else if ((event.eventType == DownloadEvent.EV_PROGRESS)
                    && (event.pagesKO > previousNbErrors)
                    && (event.content != null)) {
            currentId = event.content.getId();
            previousNbErrors = event.pagesKO;
            updateStats(currentId);
        }
    }

    private void showErrorLog() {
        Content content = ObjectBoxDB.getInstance(getContext()).selectContentById(currentId);
        if (content != null) {
            List<ErrorRecord> errorLog = content.getErrorLog();
            List<String> log = new ArrayList<>();

            LogUtil.LogInfo errorLogInfo = new LogUtil.LogInfo();
            errorLogInfo.logName = "Error";
            errorLogInfo.fileName = "error_log" + content.getId();
            errorLogInfo.noDataMessage = "No error detected.";

            log.add("Error log for " + content.getTitle() + " : " + errorLog.size() + " errors");
            for (ErrorRecord e : errorLog) log.add(e.toString());

            File logFile = LogUtil.writeLog(requireContext(), log, errorLogInfo);
            if (logFile != null) {
                Snackbar snackbar = Snackbar.make(rootView, R.string.cleanup_done, Snackbar.LENGTH_LONG);
                snackbar.setAction("READ LOG", v -> FileHelper.openFile(requireContext(), logFile));
                snackbar.show();
            }
        }
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
}
