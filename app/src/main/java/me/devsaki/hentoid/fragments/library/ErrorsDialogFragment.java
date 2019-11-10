package me.devsaki.hentoid.fragments.library;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
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
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogUtil;
import me.devsaki.hentoid.util.ToastUtil;

/**
 * Created by Robb on 11/2018
 * Info dialog for download errors details
 */
public class ErrorsDialogFragment extends DialogFragment {

    private static final String ID = "ID";

    private Parent parent;
    private View rootView;
    private Content content;

    public static void invoke(Fragment parent, long id) {
        ErrorsDialogFragment fragment = new ErrorsDialogFragment();

        Bundle args = new Bundle();
        args.putLong(ID, id);
        fragment.setArguments(args);

        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Dialog);
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

        CollectionDAO dao = new ObjectBoxDAO(getContext());
        content = dao.selectContent(id);
        if (null == content) throw new IllegalArgumentException("Content not found for ID " + id);

        rootView = view;
        updateStats(content);

        View redownloadButton = view.findViewById(R.id.redownload_btn);
        redownloadButton.setOnClickListener(v -> redownload(content));

        View openLogButton = view.findViewById(R.id.errlog_open_btn);
        openLogButton.setOnClickListener(v -> showErrorLog(content));

        View copyLogButton = view.findViewById(R.id.errlog_copy_btn);
        copyLogButton.setOnClickListener(v -> copyErrorLog(content));
    }

    private void updateStats(@NonNull final Content content) {
        int images;
        int imgErrors = 0;

        Context context = getContext();
        if (null == context) return;
        if (null == content.getImageFiles()) return;

        images = content.getImageFiles().size();

        for (ImageFile imgFile : content.getImageFiles())
            if (imgFile.getStatus() == StatusContent.ERROR) imgErrors++;

        TextView details = rootView.findViewById(R.id.redownload_detail);
        String message = context.getString(R.string.redownload_dialog_message).replace("@clean", images - imgErrors + "").replace("@error", imgErrors + "").replace("@total", images + "");
        details.setText(message);
    }

    private LogUtil.LogInfo createLog(@NonNull final Content content) {
        List<String> log = new ArrayList<>();

        LogUtil.LogInfo errorLogInfo = new LogUtil.LogInfo();
        errorLogInfo.logName = "Error";
        errorLogInfo.fileName = "error_log" + content.getId();
        errorLogInfo.noDataMessage = "No error detected.";
        errorLogInfo.log = log;

        List<ErrorRecord> errorLog = content.getErrorLog();
        if (errorLog != null) {
            log.add("Error log for " + content.getTitle() + " [" + content.getUniqueSiteId() + "@" + content.getSite().getDescription() + "] : " + errorLog.size() + " errors");
            for (ErrorRecord e : errorLog) log.add(e.toString());
        }

        return errorLogInfo;
    }

    private void showErrorLog(@NonNull final Content content) {
        ToastUtil.toast(R.string.redownload_generating_log_file);

        LogUtil.LogInfo logInfo = createLog(content);
        File logFile = LogUtil.writeLog(requireContext(), logInfo);
        if (logFile != null) FileHelper.openFile(requireContext(), logFile);
    }

    private void copyErrorLog(@NonNull final Content content) {
        LogUtil.LogInfo logInfo = createLog(content);
        if (Helper.copyPlainTextToClipboard(requireActivity(), LogUtil.buildLog(logInfo))) {
            Snackbar snackbar = Snackbar.make(rootView, R.string.redownload_log_clipboard, BaseTransientBottomBar.LENGTH_LONG);
            snackbar.show();
        }
    }

    private void redownload(@NonNull final Content content) {
        parent.downloadContent(content);
        dismiss();
    }

    public interface Parent {
        void downloadContent(final Content content);
    }
}
