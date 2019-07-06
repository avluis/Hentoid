package me.devsaki.hentoid.dirpicker.ops;

import android.content.Context;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;

import me.devsaki.hentoid.dirpicker.adapter.DirAdapter;
import me.devsaki.hentoid.dirpicker.model.DirTree;

/**
 * Created by avluis on 06/12/2016.
 * Directory List Builder
 */
public class DirListBuilder {
    private final DirTree dirTree;
    private final ListDir listDir;

    private RecyclerView.Adapter adapter;

    public DirListBuilder(Context context, RecyclerView recyclerView) {
        dirTree = new DirTree();
        listDir = new ListDir(dirTree);
        attachRecyclerView(context, recyclerView);
    }

    private void attachRecyclerView(Context context, RecyclerView recyclerView) {
        adapter = new DirAdapter(dirTree.dirList);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);
    }

    public void processListDirEvent(File rootDir) {
        listDir.process(rootDir);
    }

    public void notifyDatasetChanged() {
        adapter.notifyDataSetChanged();
    }

    public void dispose()
    {
        if (listDir != null) listDir.dispose();
    }
}
