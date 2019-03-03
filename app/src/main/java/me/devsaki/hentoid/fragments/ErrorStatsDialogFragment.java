package me.devsaki.hentoid.fragments;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.events.DownloadErrorEvent;
import me.devsaki.hentoid.events.DownloadEvent;

/**
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class ErrorStatsDialogFragment extends DialogFragment {

    private TextView details;
    private Map<ErrorType, Integer> errors = new HashMap<>();

    public static void invoke(FragmentManager fragmentManager) {
        ErrorStatsDialogFragment fragment = new ErrorStatsDialogFragment();

        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.DownloadsDialog);
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.fragment_error_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        details = view.findViewById(R.id.stats_details);

        Button okButton = view.findViewById(R.id.stats_ok);
        okButton.setOnClickListener(v -> this.dismiss());
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onErrorEvent(DownloadErrorEvent event) {
        if (errors.containsKey(event.error.type)) {
            int nbErrors = errors.get(event.error.type);
            errors.put(event.error.type, ++nbErrors);
        } else {
            errors.put(event.error.type, 1);
        }

        StringBuilder detailsStr = new StringBuilder();

        for (ErrorType type : errors.keySet()) {
            detailsStr.append(type.getName()).append(" : ");
            detailsStr.append(errors.get(type));
            detailsStr.append(System.getProperty("line.separator"));
        }

        details.setText(detailsStr.toString());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (event.eventType == DownloadEvent.EV_COMPLETE) {
            details.setText("Download complete");
        }
    }
}
