package me.devsaki.hentoid.fragments.tools;

import static androidx.core.view.ViewCompat.requireViewById;

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

import com.annimon.stream.Stream;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.skydoves.powermenu.MenuAnimation;
import com.skydoves.powermenu.PowerMenu;
import com.skydoves.powermenu.PowerMenuItem;

import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewholders.TextItem;
import timber.log.Timber;

/**
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
        rootView = inflater.inflate(R.layout.dialog_tools_app_logs, container, false);

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
            fileName = (null == fileName) ? "" : fileName.toLowerCase();

            String label;
            if (fileName.startsWith("import_external"))
                label = getResources().getString(R.string.log_import_external);
            else if (fileName.startsWith("import"))
                label = getResources().getString(R.string.log_import);
            else if (fileName.startsWith("cleanup"))
                label = getResources().getString(R.string.log_cleanup);
            else if (fileName.startsWith("api29_migration"))
                label = getResources().getString(R.string.log_api29_migration);
            else label = "[" + fileName + "]";

            Instant lastModified = Instant.ofEpochMilli(file.lastModified());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.ENGLISH);
            String timeStr = lastModified.atZone(ZoneId.systemDefault()).format(formatter);
            label += " (" + timeStr + ")";

            itemAdapter.add(new TextItem<>(label, file, false));
        }
    }

    private List<DocumentFile> getLogs() {
        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(requireContext(), Preferences.getStorageUri());
        if (null == rootFolder) return Collections.emptyList();

        List<DocumentFile> files = FileHelper.listFiles(requireContext(), rootFolder, displayName -> displayName.toLowerCase().endsWith("_log.txt"));
        // Sort by date desc
        files = Stream.of(files).sortBy(DocumentFile::lastModified).toList();
        Collections.reverse(files);
        return files;
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
                .setAutoDismiss(true)
                .build();

        powerMenu.setOnMenuItemClickListener((p, i) -> {
            if (0 == p) {
                FileHelper.openFile(requireContext(), document);
            } else {
                FileHelper.shareFile(requireContext(), document.getUri(), item.getText());
            }
        });

        powerMenu.setIconColor(ContextCompat.getColor(requireContext(), R.color.white_opacity_87));
        powerMenu.showAtCenter(rootView);

        return true;
    }
}
