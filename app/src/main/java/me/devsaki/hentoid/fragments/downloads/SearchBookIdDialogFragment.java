package me.devsaki.hentoid.fragments.downloads;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.adapters.SiteAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;

/**
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class SearchBookIdDialogFragment extends DialogFragment {

    private static String ID = "ID";
    private static String FOUND_SITES = "FOUND_SITES";

    private String bookId;

    public static void invoke(FragmentManager fragmentManager, String id, ArrayList<Integer> siteCodes) {
        Bundle args = new Bundle();
        args.putString(ID, id);
        args.putIntegerArrayList(FOUND_SITES, siteCodes);

        SearchBookIdDialogFragment fragment = new SearchBookIdDialogFragment();
        fragment.setArguments(args);
        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Dialog);
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
            ArrayList<Integer> foundSitesList = getArguments().getIntegerArrayList(FOUND_SITES);

            TextView title = view.findViewById(R.id.search_bookid_title);
            title.setText(getString(R.string.search_bookid_label, bookId));

            // Not possible for Pururin, e-hentai
            List<Site> sites = new ArrayList<>();
            if (foundSitesList != null) {
                if (!foundSitesList.contains(Site.HITOMI.getCode())) sites.add(Site.HITOMI);
                if (!foundSitesList.contains(Site.NHENTAI.getCode())) sites.add(Site.NHENTAI);
                if (!foundSitesList.contains(Site.ASMHENTAI.getCode())) sites.add(Site.ASMHENTAI);
                if (!foundSitesList.contains(Site.ASMHENTAI_COMICS.getCode()))
                    sites.add(Site.ASMHENTAI_COMICS);
                if (!foundSitesList.contains(Site.HENTAICAFE.getCode())) sites.add(Site.HENTAICAFE);
                if (!foundSitesList.contains(Site.TSUMINO.getCode())) sites.add(Site.TSUMINO);
                if (!foundSitesList.contains(Site.NEXUS.getCode())) sites.add(Site.NEXUS);
            }

            SiteAdapter siteAdapter = new SiteAdapter();
            siteAdapter.setOnClickListener(this::onItemSelected);
            siteAdapter.add(sites);

            RecyclerView sitesRecycler = view.findViewById(R.id.select_sites);
            sitesRecycler.setAdapter(siteAdapter);
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
            case NEXUS:
                return site.getUrl() + "/view/" + id;
            default:
                return site.getUrl();
        }
    }

    private void onItemSelected(View view) {
        Site s = (Site) view.getTag();

        if (s != null) {
            Intent intent = new Intent(requireContext(), Content.getWebActivityClass(s));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            BaseWebActivityBundle.Builder builder = new BaseWebActivityBundle.Builder();
            builder.setUrl(getUrlFromId(s, bookId));
            intent.putExtras(builder.getBundle());

            requireContext().startActivity(intent);
            this.dismiss();
        }
    }
}
