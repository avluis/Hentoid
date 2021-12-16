package me.devsaki.hentoid.fragments.library;

import static androidx.core.view.ViewCompat.requireViewById;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.viewholders.TextItem;

/**
 * Launcher dialog for the library refresh feature
 */
public class SearchContentIdDialogFragment extends DialogFragment {

    private static final String ID = "ID";
    private static final String FOUND_SITES = "FOUND_SITES";

    private String bookId;

    public static void invoke(Context context, FragmentManager fragmentManager, String id, ArrayList<Integer> siteCodes) {
        Bundle args = new Bundle();
        args.putString(ID, id);
        args.putIntegerArrayList(FOUND_SITES, siteCodes);

        SearchContentIdDialogFragment fragment = new SearchContentIdDialogFragment();
        ThemeHelper.setStyle(context, fragment, STYLE_NORMAL, R.style.Theme_Light_BottomSheetDialog);
        fragment.setArguments(args);
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_library_search_id, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        if (getArguments() != null) {
            bookId = getArguments().getString(ID, "");
            ArrayList<Integer> foundSitesList = getArguments().getIntegerArrayList(FOUND_SITES);

            TextView title = requireViewById(rootView, R.id.search_bookid_title);
            title.setText(getString(R.string.search_bookid_label, bookId));

            // Not possible for Pururin, e-hentai, exhentai
            List<Site> sites = new ArrayList<>();
            if (foundSitesList != null) {
                if (!foundSitesList.contains(Site.HITOMI.getCode())) sites.add(Site.HITOMI);
                if (!foundSitesList.contains(Site.NHENTAI.getCode())) sites.add(Site.NHENTAI);
                if (!foundSitesList.contains(Site.ASMHENTAI.getCode())) sites.add(Site.ASMHENTAI);
                if (!foundSitesList.contains(Site.ASMHENTAI_COMICS.getCode()))
                    sites.add(Site.ASMHENTAI_COMICS);
                if (!foundSitesList.contains(Site.TSUMINO.getCode())) sites.add(Site.TSUMINO);
                if (!foundSitesList.contains(Site.LUSCIOUS.getCode())) sites.add(Site.LUSCIOUS);
                if (!foundSitesList.contains(Site.HBROWSE.getCode())) sites.add(Site.HBROWSE);
                if (!foundSitesList.contains(Site.HENTAIFOX.getCode())) sites.add(Site.HENTAIFOX);
                if (!foundSitesList.contains(Site.IMHENTAI.getCode())) sites.add(Site.IMHENTAI);
                if (!foundSitesList.contains(Site.PIXIV.getCode())) sites.add(Site.PIXIV);
            }
            ItemAdapter<TextItem<Site>> itemAdapter = new ItemAdapter<>();
            itemAdapter.set(Stream.of(sites).map(s -> new TextItem<>(s.getDescription(), s, true)).toList());

            // Item click listener
            FastAdapter<TextItem<Site>> fastAdapter = FastAdapter.with(itemAdapter);
            fastAdapter.setOnClickListener((v, a, i, p) -> onItemSelected(i.getTag()));

            RecyclerView sitesRecycler = requireViewById(rootView, R.id.select_sites);
            sitesRecycler.setAdapter(fastAdapter);
        }
    }

    private static String getUrlFromId(Site site, String id) {
        switch (site) {
            case HITOMI:
                return site.getUrl() + "/galleries/" + id + ".html";
            case NHENTAI:
            case ASMHENTAI:
            case ASMHENTAI_COMICS:
                return site.getUrl() + "/g/" + id + "/";
            case IMHENTAI:
            case HENTAIFOX:
                return site.getUrl() + "/gallery/" + id + "/";
            case HENTAICAFE:
                return site.getUrl() + "/hc.fyi/" + id;
            case TSUMINO:
                return site.getUrl() + "/entry/" + id;
            case NEXUS:
                return site.getUrl() + "/view/" + id;
            case LUSCIOUS:
                return site.getUrl().replace("manga", "albums") + id + "/";
            case HBROWSE:
                return site.getUrl() + id + "/c00001";
            case PIXIV:
                return site.getUrl() + "artworks/" + id;
            default:
                return site.getUrl();
        }
    }

    private boolean onItemSelected(Site s) {
        if (null == s) return false;

        ContentHelper.launchBrowserFor(requireContext(), getUrlFromId(s, bookId));

        this.dismiss();
        return true;
    }
}
