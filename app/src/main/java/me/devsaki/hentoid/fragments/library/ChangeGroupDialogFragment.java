package me.devsaki.hentoid.fragments.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;
import com.skydoves.powerspinner.PowerSpinnerView;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.util.GroupHelper;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;

/**
 * Created by Robb on 09/2020
 * Dialog to select or create a custom group
 */
public class ChangeGroupDialogFragment extends DialogFragment {

    private static final String BOOK_IDS = "BOOK_IDS";

    private long[] bookIds;
    private List<Group> customGroups;

    private RadioButton existingRadio;
    private PowerSpinnerView existingSpin;
    private RadioButton newRadio;
    private EditText newNameTxt;


    public static void invoke(FragmentManager fragmentManager, long[] bookIds) {
        Bundle args = new Bundle();
        args.putLongArray(BOOK_IDS, bookIds);

        ChangeGroupDialogFragment fragment = new ChangeGroupDialogFragment();
//        ThemeHelper.setStyle(context, fragment, STYLE_NORMAL, R.style.Theme_Light_BottomSheetDialog);
        fragment.setArguments(args);
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_library_change_group, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        if (getArguments() != null) {
            bookIds = getArguments().getLongArray(BOOK_IDS);

            existingRadio = rootView.findViewById(R.id.change_group_existing_radio);
            existingSpin = rootView.findViewById(R.id.change_group_existing_list);
            newRadio = rootView.findViewById(R.id.change_group_new_radio);
            newNameTxt = rootView.findViewById(R.id.change_group_new_name);

            // Get existing custom groups
            CollectionDAO dao = new ObjectBoxDAO(requireContext());
            try {
                customGroups = dao.selectGroups(Grouping.CUSTOM.getId());
                customGroups = Stream.of(customGroups).filterNot(g -> (GroupHelper.FLAG_UNCATEGORIZED == g.flag)).toList();

                if (customGroups.size() > 0) { // "Existing group" by default
                    existingRadio.setChecked(true);
                    existingSpin.setVisibility(View.VISIBLE);
                    existingSpin.setItems(Stream.of(customGroups).map(g -> g.name).toList());

                    // If there's only one content selected, indicate its group
                    if (1 == bookIds.length) {
                        List<GroupItem> gi = dao.selectGroupItems(bookIds[0], Grouping.CUSTOM);
                        if (!gi.isEmpty())
                            for (int i = 0; i < customGroups.size(); i++) {
                                if (gi.get(0).group.getTargetId() == customGroups.get(i).id) {
                                    existingSpin.selectItemByIndex(i);
                                    break;
                                }
                            }
                    }

                } else { // If none of them exist, "new group" is suggested by default
                    existingRadio.setVisibility(View.GONE);
                    newRadio.setChecked(true);
                    newNameTxt.setVisibility(View.VISIBLE);
                }

                // Radio logic
                existingRadio.setOnCheckedChangeListener((v, b) -> onExistingRadioSelect(b));
                newRadio.setOnCheckedChangeListener((v, b) -> onNewRadioSelect(b));

                // Item click listener
                rootView.findViewById(R.id.change_ok_btn).setOnClickListener(v -> onOkClick());
            } finally {
                dao.cleanup();
            }
        }
    }

    private void onExistingRadioSelect(boolean isChecked) {
        existingSpin.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        newNameTxt.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        newRadio.setChecked(!isChecked);
    }

    private void onNewRadioSelect(boolean isChecked) {
        existingSpin.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        newNameTxt.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        existingRadio.setChecked(!isChecked);
    }

    private void onOkClick() {
        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        LibraryViewModel viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(LibraryViewModel.class);

        if (existingRadio.isChecked()) {
            viewModel.moveBooks(bookIds, customGroups.get(existingSpin.getSelectedIndex()));
            dismiss();
        } else {
            List<Group> groupMatchingName = Stream.of(customGroups).filter(g -> g.name.equalsIgnoreCase(newNameTxt.getText().toString())).toList();
            if (groupMatchingName.isEmpty()) { // No existing group with same name -> OK
                viewModel.moveBooks(bookIds, newNameTxt.getText().toString());
                dismiss();
            } else {
                ToastUtil.toast(R.string.group_name_exists);
            }
        }
    }
}
