package me.devsaki.hentoid.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Consts;

/**
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class SearchBookIdDialogFragment extends DialogFragment {

    private static String ID = "ID";

    private Site currentSite = null;

    public static void invoke(FragmentManager fragmentManager, String id) {
        SearchBookIdDialogFragment fragment = new SearchBookIdDialogFragment();

        Bundle args = new Bundle();
        args.putString(ID, id);
        fragment.setArguments(args);

        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.DownloadsDialog);
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.fragment_search_bookid, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            String id = getArguments().getString(ID, "");

            TextView title = view.findViewById(R.id.search_bookid_title);
            title.setText(String.format(getText(R.string.search_bookid_label).toString(), id));

            // Not possible for Pururin, e-hentai
            List<Site> sites = new ArrayList<>();
            sites.add(Site.HITOMI);
            sites.add(Site.NHENTAI);
            sites.add(Site.ASMHENTAI);
            sites.add(Site.ASMHENTAI_COMICS);
            sites.add(Site.HENTAICAFE);
            sites.add(Site.TSUMINO);

            currentSite = sites.get(0);

            ArrayAdapter<Site> dataAdapter = new ArrayAdapter<>(view.getContext(), android.R.layout.simple_spinner_item, sites);
            dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            Spinner spinner = view.findViewById(R.id.search_bookid_spinner);
            spinner.setAdapter(dataAdapter);
            spinner.setOnItemSelectedListener(new ItemSelectedListener());

            Button actionButton = view.findViewById(R.id.search_bookid_searchbtn);
            actionButton.setOnClickListener(v -> searchSite(id));
        }
    }

    private static String getUrlFromId(Site site, String id) {
        switch (site) {
            case HITOMI:
                return site.getUrl() + "/galleries/" + id + ".html";
            case NHENTAI:
                return site.getUrl() + "/g/" + id + "/";
            case ASMHENTAI:
                return site.getUrl() + "/g/" + id + "/";
            case ASMHENTAI_COMICS:
                return site.getUrl() + "/g/" + id + "/";
            case HENTAICAFE:
                return site.getUrl() + "/?p=" + id;
            case TSUMINO:
                return site.getUrl() + "/Book/Info/" + id + "/";
            default:
                return site.getUrl();
        }
    }

    private void searchSite(String id) {
        Intent intent = new Intent(requireContext(), Content.getWebActivityClass(currentSite));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Consts.INTENT_URL, getUrlFromId(currentSite, id));
        requireContext().startActivity(intent);
        this.dismiss();
    }

    public class ItemSelectedListener implements AdapterView.OnItemSelectedListener {

        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            currentSite = (Site) parent.getItemAtPosition(pos);
        }

        public void onNothingSelected(AdapterView parent) {
            // Do nothing.
        }
    }
}
