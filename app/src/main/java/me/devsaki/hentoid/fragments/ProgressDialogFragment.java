package me.devsaki.hentoid.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.databinding.DialogProgressBinding;
import me.devsaki.hentoid.events.ProcessEvent;

/**
 * Generic dialog to report progress
 */
public class ProgressDialogFragment extends DialogFragment {

    private static final String TITLE = "title";
    private static final String PROGRESS_UNIT = "progressUnit";
    private DialogProgressBinding binding = null;

    private String dialogTitle;
    private String progressUnit;

    public static void invoke(
            @NonNull final FragmentManager fragmentManager,
            @NonNull final String title,
            @NonNull final String progressUnit) {
        ProgressDialogFragment fragment = new ProgressDialogFragment();

        Bundle args = new Bundle();
        args.putString(TITLE, title);
        args.putString(PROGRESS_UNIT, progressUnit);
        fragment.setArguments(args);

        fragment.show(fragmentManager, null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        dialogTitle = getArguments().getString(TITLE, "");
        progressUnit = getArguments().getString(PROGRESS_UNIT, "");

        EventBus.getDefault().register(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        binding = DialogProgressBinding.inflate(inflater, container, false);
        setCancelable(false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        binding.title.setText(dialogTitle);
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        super.onDestroyView();
        binding = null;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onProcessEvent(ProcessEvent event) {
        if (event.processId != R.id.generic_progress) return;

        binding.bar.setMax(event.elementsTotal);
        if (ProcessEvent.EventType.PROGRESS == event.eventType) {
            binding.progress.setText(getString(R.string.generic_progress, event.elementsOK + event.elementsKO, event.elementsTotal, progressUnit));
            binding.bar.setProgress(event.elementsOK + event.elementsKO);
        } else if (ProcessEvent.EventType.COMPLETE == event.eventType) {
            dismiss();
        }
    }
}
