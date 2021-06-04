package me.devsaki.hentoid.fragments.library;

import android.content.Context;
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

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.util.ToastHelper;

/**
 * Created by Robb on 11/2018
 * Info dialog for download errors details
 */
public class ErrorsDialogFragment extends DialogFragment {

    private static final String ID = "ID";

    private Parent parent;
    private View rootView;

    public static void invoke(Fragment parent, long id) {
        ErrorsDialogFragment fragment = new ErrorsDialogFragment();

        Bundle args = new Bundle();
        args.putLong(ID, id);
        fragment.setArguments(args);

        fragment.show(parent.getChildFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parent = (Parent) getParentFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_library_errors, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        long id = getArguments().getLong(ID, 0);
        if (0 == id) throw new IllegalArgumentException("No ID found");

        Content content;
        CollectionDAO dao = new ObjectBoxDAO(getContext());
        try {
            content = dao.selectContent(id);
        } finally {
            dao.cleanup();
        }
        if (null == content) throw new IllegalArgumentException("Content not found for ID " + id);

        rootView = view;
        updateStats(content);

        View redownloadButton = view.findViewById(R.id.redownload_btn);
        redownloadButton.setOnClickListener(v -> redownload(content));

        View openLogButton = view.findViewById(R.id.open_log_btn);
        openLogButton.setOnClickListener(v -> showErrorLog(content));

        View shareLogButton = view.findViewById(R.id.share_log_btn);
        shareLogButton.setOnClickListener(v -> shareErrorLog(content));
    }

    private void updateStats(@NonNull final Content content) {
        int images;
        int imgErrors = 0;

        Context context = getContext();
        if (null == context) return;
        if (null == content.getImageFiles()) return;

        images = content.getImageFiles().size() - 1; // Don't count the cover

        for (ImageFile imgFile : content.getImageFiles())
            if (imgFile.getStatus() == StatusContent.ERROR) imgErrors++;

        if (0 == images) {
            images = content.getQtyPages();
            imgErrors = images;
        }

        TextView details = rootView.findViewById(R.id.redownload_detail);
        details.setText(context.getString(R.string.redownload_dialog_message, images, images - imgErrors, imgErrors));

        if (content.getErrorLog() != null && !content.getErrorLog().isEmpty()) {
            TextView firstErrorTxt = rootView.findViewById(R.id.redownload_detail_first_error);
            ErrorRecord firstError = content.getErrorLog().get(0);
            String message = context.getString(R.string.redownload_first_error, firstError.getType().getName());
            if (!firstError.getDescription().isEmpty())
                message += String.format(" - %s", firstError.getDescription());
            firstErrorTxt.setText(message);
            firstErrorTxt.setVisibility(View.VISIBLE);
        }
    }

    private LogHelper.LogInfo createLog(@NonNull final Content content) {
        List<LogHelper.LogEntry> log = new ArrayList<>();

        LogHelper.LogInfo errorLogInfo = new LogHelper.LogInfo();
        errorLogInfo.setLogName("Error");
        errorLogInfo.setFileName("error_log" + content.getId());
        errorLogInfo.setNoDataMessage("No error detected.");
        errorLogInfo.setEntries(log);

        List<ErrorRecord> errorLog = content.getErrorLog();
        if (errorLog != null) {
            errorLogInfo.setHeader("Error log for " + content.getTitle() + " [" + content.getUniqueSiteId() + "@" + content.getSite().getDescription() + "] : " + errorLog.size() + " errors");
            for (ErrorRecord e : errorLog)
                log.add(new LogHelper.LogEntry(e.getTimestamp(), e.toString()));
        }

        return errorLogInfo;
    }

    private void showErrorLog(@NonNull final Content content) {
        ToastHelper.toast(R.string.redownload_generating_log_file);

        LogHelper.LogInfo logInfo = createLog(content);
        DocumentFile logFile = LogHelper.writeLog(requireContext(), logInfo);
        if (logFile != null) FileHelper.openFile(requireContext(), logFile);
    }

    private void shareErrorLog(@NonNull final Content content) {
        LogHelper.LogInfo logInfo = createLog(content);
        DocumentFile logFile = LogHelper.writeLog(requireContext(), logInfo);
        if (logFile != null)
            FileHelper.shareFile(requireContext(), logFile.getUri(), "Error log for book ID " + content.getUniqueSiteId());
    }

    private void redownload(@NonNull final Content content) {
        parent.redownloadContent(content);
        dismiss();
    }

    public interface Parent {
        void redownloadContent(final Content content);
    }
}
