package me.devsaki.hentoid.fragments.intro;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.viewholders.SiteFlex;

public class SourcesIntroFragment extends Fragment {

    private FlexibleAdapter<SiteFlex> siteAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.intro_slide_06, container, false);

        // Recycler
        List<SiteFlex> items = new ArrayList<>();
        for (Site s : Site.values())
            // We don't want to show these
            if (s != Site.FAKKU                     // Old Fakku; kept for retrocompatibility
                    && s != Site.ASMHENTAI_COMICS   // Does not work directly
                    && s != Site.PANDA              // Dropped; kept for retrocompatibility
                    && s != Site.NONE               // Technical fallback
            ) items.add(new SiteFlex(s, true, false));

        siteAdapter = new FlexibleAdapter<>(items, null, true);
        RecyclerView recyclerView = view.findViewById(R.id.intro6_list);
        recyclerView.setAdapter(siteAdapter);

        return view;
    }

    public List<Site> getSelection() {
        List<Site> result = new ArrayList<>();
        for (SiteFlex s : siteAdapter.getCurrentItems())
            if (s.isSelected()) result.add(s.getSite());
        return result;
    }
}
