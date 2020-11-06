package me.devsaki.hentoid.fragments.preferences;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.skydoves.powermenu.MenuAnimation;
import com.skydoves.powermenu.PowerMenu;
import com.skydoves.powermenu.PowerMenuItem;

import java.util.Collections;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewholders.TextItem;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 11/2020
 * Dialog to view app logs
 */
public class LogsDialogFragment extends DialogFragment {

    private View rootView;

    private final ItemAdapter<TextItem<DocumentFile>> itemAdapter = new ItemAdapter<>();
    private Disposable disposable;

    public static void invoke(@NonNull final FragmentManager fragmentManager) {
        LogsDialogFragment fragment = new LogsDialogFragment();
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        rootView = inflater.inflate(R.layout.dialog_prefs_logs, container, false);

        FastAdapter<TextItem<DocumentFile>> fastadapter = FastAdapter.with(itemAdapter);
        RecyclerView recyclerView = requireViewById(rootView, R.id.logs_list);
        recyclerView.setAdapter(fastadapter);

        fastadapter.setOnClickListener((v, a, i, p) -> onItemClick(i));

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        disposable = Single.fromCallable(this::getLogs)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onGetSuccess, Timber::w);
    }

    private void onGetSuccess(List<DocumentFile> files) {
        disposable.dispose();
        for (DocumentFile file : files) {
            String fileName = file.getName();
            if (null == fileName) fileName = "";

            // TODO format
            itemAdapter.add(new TextItem<>(fileName, file, false));
        }
    }

    private List<DocumentFile> getLogs() {
        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(requireContext(), Preferences.getStorageUri());
        if (null == rootFolder) return Collections.emptyList();

        return FileHelper.listFiles(requireContext(), rootFolder, displayName -> displayName.toLowerCase().endsWith("_log.txt"));
    }

    private boolean onItemClick(TextItem<DocumentFile> item) {
        DocumentFile document = item.getTag();
        if (null == document) return false;

        PowerMenu powerMenu = new PowerMenu.Builder(requireContext())
                .addItem(new PowerMenuItem(getResources().getString(R.string.logs_open), R.drawable.ic_action_open_in_new, false))
                .addItem(new PowerMenuItem(getResources().getString(R.string.logs_share), R.drawable.ic_action_share, false))
                .setAnimation(MenuAnimation.SHOWUP_TOP_LEFT)
                .setMenuRadius(10f)
                .setLifecycleOwner(requireActivity())
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.white_opacity_87))
                .setTextTypeface(Typeface.DEFAULT)
                .setMenuColor(ContextCompat.getColor(requireContext(), R.color.dark_gray))
                .setTextSize(Helper.dimensAsDp(requireContext(), R.dimen.text_subtitle_1))
                .build();

        powerMenu.setOnMenuItemClickListener((p, i) -> {
            if (0 == p) {
                FileHelper.openFile(requireContext(), document);
            } else {
                FileHelper.shareFile(requireContext(), document.getUri(), item.getText());
            }
            powerMenu.dismiss();
        });

        powerMenu.setIconColor(ContextCompat.getColor(requireContext(), R.color.white_opacity_87));
        powerMenu.showAtCenter(rootView);

        return true;
    }
}
