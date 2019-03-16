package me.devsaki.hentoid.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.adapters.SiteAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.Helper;

/**
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class SearchBookIdDialogFragment extends DialogFragment {

    private static String ID = "ID";
    private static String FOUND_SITES = "FOUND_SITES";

    private String bookId;

    public static void invoke(FragmentManager fragmentManager, String id, List<Integer> foundSiteCodes) {
        SearchBookIdDialogFragment fragment = new SearchBookIdDialogFragment();

        Bundle args = new Bundle();
        args.putString(ID, id);
        args.putIntArray(FOUND_SITES, Helper.getPrimitiveIntArrayFromList(foundSiteCodes));
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
            bookId = getArguments().getString(ID, "");
            int[] foundSites = getArguments().getIntArray(FOUND_SITES);
            List<Integer> foundSitesList = new ArrayList<>();
            if (foundSites != null)
                foundSitesList.addAll(Helper.getListFromPrimitiveArray(foundSites));

            TextView title = view.findViewById(R.id.search_bookid_title);
            title.setText(String.format(getText(R.string.search_bookid_label).toString(), bookId));

            // Not possible for Pururin, e-hentai
            List<Site> sites = new ArrayList<>();
            if (!foundSitesList.contains(Site.HITOMI.getCode())) sites.add(Site.HITOMI);
            if (!foundSitesList.contains(Site.NHENTAI.getCode())) sites.add(Site.NHENTAI);
            if (!foundSitesList.contains(Site.ASMHENTAI.getCode())) sites.add(Site.ASMHENTAI);
            if (!foundSitesList.contains(Site.ASMHENTAI_COMICS.getCode()))
                sites.add(Site.ASMHENTAI_COMICS);
            if (!foundSitesList.contains(Site.HENTAICAFE.getCode())) sites.add(Site.HENTAICAFE);
            if (!foundSitesList.contains(Site.TSUMINO.getCode())) sites.add(Site.TSUMINO);

            RecyclerView sitesRecycler = view.findViewById(R.id.select_sites);
            sitesRecycler.setLayoutManager(new LinearLayoutManager(this.getContext()));
            SiteAdapter siteAdapter = new SiteAdapter();
            siteAdapter.setOnClickListener(this::onItemSelected);
            sitesRecycler.setAdapter(siteAdapter);
            siteAdapter.add(sites);
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

    private void onItemSelected(View view) {
        Site s = (Site) view.getTag();

        if (s != null) {
            Intent intent = new Intent(requireContext(), Content.getWebActivityClass(s));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Consts.INTENT_URL, getUrlFromId(s, bookId));
            requireContext().startActivity(intent);
            this.dismiss();
        }
    }
}
