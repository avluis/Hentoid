package me.devsaki.hentoid.fragments.intro;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.viewholders.SiteItem;

public class SourcesIntroFragment extends Fragment {

    private final ItemAdapter<SiteItem> itemAdapter = new ItemAdapter<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.intro_slide_06, container, false);

        // Recycler
        List<SiteItem> items = new ArrayList<>();
        for (Site s : Site.values())
            if (s.isVisible()) items.add(new SiteItem(s, true, false));
        itemAdapter.add(items);

        FastAdapter<SiteItem> fastAdapter = FastAdapter.with(itemAdapter);
        RecyclerView recyclerView = view.findViewById(R.id.list);
        recyclerView.setAdapter(fastAdapter);

        return view;
    }

    public List<Site> getSelection() {
        List<Site> result = new ArrayList<>();
        for (SiteItem s : itemAdapter.getAdapterItems())
            if (s.isSelected()) result.add(s.getSite());
        return result;
    }
}
