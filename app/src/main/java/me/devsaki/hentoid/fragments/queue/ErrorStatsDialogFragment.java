package me.devsaki.hentoid.fragments.queue;

import static androidx.core.view.ViewCompat.requireViewById;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.util.ToastHelper;

/**
 * Info dialog for download errors details
 */
public class ErrorStatsDialogFragment extends DialogFragment {

    private static final String ID = "ID";

    private TextView details;
    private int previousNbErrors;
    private long currentId;
    private View rootView;

    public static void invoke(Fragment parent, long id) {
        ErrorStatsDialogFragment fragment = new ErrorStatsDialogFragment();

        Bundle args = new Bundle();
        args.putLong(ID, id);
        fragment.setArguments(args);

        fragment.show(parent.getChildFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_queue_errors, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        this.rootView = rootView;

        if (getArguments() != null) {
            details = requireViewById(rootView, R.id.stats_details);
            details.setText(R.string.downloads_loading);

            previousNbErrors = 0;
            long id = getArguments().getLong(ID, 0);
            currentId = id;
            if (id > 0) updateStats(id);
        }

        View openLogButton = requireViewById(rootView, R.id.open_log_btn);
        openLogButton.setOnClickListener(v -> this.showErrorLog());

        View shareLogButton = requireViewById(rootView, R.id.share_log_btn);
        shareLogButton.setOnClickListener(v -> this.shareErrorLog());
    }

    private void updateStats(long contentId) {
        CollectionDAO dao = new ObjectBoxDAO(requireContext());
        List<ErrorRecord> errors;
        try {
            errors = dao.selectErrorRecordByContentId(contentId);
        } finally {
            dao.cleanup();
        }
        Map<ErrorType, Integer> errorsByType = new EnumMap<>(ErrorType.class);

        for (ErrorRecord error : errors) {
            if (errorsByType.containsKey(error.getType())) {
                Integer nbErrorsObj = errorsByType.get(error.getType());
                int nbErrors = (null == nbErrorsObj) ? 0 : nbErrorsObj;
                errorsByType.put(error.getType(), ++nbErrors);
            } else {
                errorsByType.put(error.getType(), 1);
            }
        }

        StringBuilder detailsStr = new StringBuilder();

        for (Map.Entry<ErrorType, Integer> entry : errorsByType.entrySet()) {
            detailsStr.append(entry.getKey().getName()).append(" : ");
            detailsStr.append(entry.getValue());
            detailsStr.append(System.getProperty("line.separator"));
        }

        details.setText(detailsStr.toString());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (event.eventType == DownloadEvent.Type.EV_COMPLETE) {
            details.setText(R.string.download_complete);
            previousNbErrors = 0;
        } else if (event.eventType == DownloadEvent.Type.EV_CANCEL) {
            details.setText(R.string.download_cancelled);
            previousNbErrors = 0;
        } else if ((event.eventType == DownloadEvent.Type.EV_PROGRESS)
                && (event.pagesKO > previousNbErrors)
                && (event.content != null)) {
            currentId = event.content.getId();
            previousNbErrors = event.pagesKO;
            updateStats(currentId);
        }
    }

    private LogHelper.LogInfo createLog() {
        Content content;
        CollectionDAO dao = new ObjectBoxDAO(getContext());
        try {
            content = dao.selectContent(currentId);
        } finally {
            dao.cleanup();
        }

        if (null == content) {
            Snackbar snackbar = Snackbar.make(rootView, R.string.content_not_found, BaseTransientBottomBar.LENGTH_LONG);
            snackbar.show();
            return new LogHelper.LogInfo();
        }

        List<LogHelper.LogEntry> log = new ArrayList<>();

        LogHelper.LogInfo errorLogInfo = new LogHelper.LogInfo();
        errorLogInfo.setHeaderName(getResources().getString(R.string.error));
        errorLogInfo.setFileName("error_log" + content.getId());
        errorLogInfo.setNoDataMessage(getResources().getString(R.string.no_error_detected));
        errorLogInfo.setEntries(log);

        List<ErrorRecord> errorLog = content.getErrorLog();
        if (errorLog != null) {
            errorLogInfo.setHeader(getResources().getString(R.string.error_log_header, content.getTitle(), content.getUniqueSiteId(), content.getSite().getDescription(), errorLog.size()));
            for (ErrorRecord e : errorLog)
                log.add(new LogHelper.LogEntry(e.getTimestamp(), e.toString()));
        }

        return errorLogInfo;
    }

    private void showErrorLog() {
        ToastHelper.toast(R.string.redownload_generating_log_file);

        LogHelper.LogInfo logInfo = createLog();
        DocumentFile logFile = LogHelper.writeLog(requireContext(), logInfo);
        if (logFile != null) FileHelper.openFile(requireContext(), logFile);
    }

    private void shareErrorLog() {
        LogHelper.LogInfo logInfo = createLog();
        DocumentFile logFile = LogHelper.writeLog(requireContext(), logInfo);
        if (logFile != null)
            FileHelper.shareFile(requireContext(), logFile.getUri(), getResources().getString(R.string.error_log_header_queue));
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
}
