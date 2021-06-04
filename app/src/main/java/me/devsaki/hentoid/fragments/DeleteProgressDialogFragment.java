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
import me.devsaki.hentoid.databinding.DialogPrefsDeleteBinding;
import me.devsaki.hentoid.events.ProcessEvent;

/**
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class DeleteProgressDialogFragment extends DialogFragment {

    private static final String TITLE = "title";
    private DialogPrefsDeleteBinding binding = null;

    private String dialogTitle;

    public static void invoke(@NonNull final FragmentManager fragmentManager, @NonNull final String title) {
        DeleteProgressDialogFragment fragment = new DeleteProgressDialogFragment();

        Bundle args = new Bundle();
        args.putString(TITLE, title);
        fragment.setArguments(args);

        fragment.show(fragmentManager, null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        dialogTitle = getArguments().getString(TITLE, "");

        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        super.onDestroyView();
        binding = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        binding = DialogPrefsDeleteBinding.inflate(inflater, container, false);
        setCancelable(false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        binding.deleteTitle.setText(dialogTitle);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onProcessEvent(ProcessEvent event) {
        if (event.processId != R.id.generic_delete) return;

        binding.deleteBar.setMax(event.elementsTotal);
        if (ProcessEvent.EventType.PROGRESS == event.eventType) {
            binding.deleteProgress.setText(getString(R.string.book_progress, event.elementsOK + event.elementsKO, event.elementsTotal));
            binding.deleteBar.setProgress(event.elementsOK + event.elementsKO);
        } else if (ProcessEvent.EventType.COMPLETE == event.eventType) {
            dismiss();
        }
    }
}
