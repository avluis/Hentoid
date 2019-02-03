package me.devsaki.hentoid.dirpicker.ops;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import java.io.File;

import me.devsaki.hentoid.dirpicker.adapter.DirAdapter;
import me.devsaki.hentoid.dirpicker.model.DirTree;

/**
 * Created by avluis on 06/12/2016.
 * Directory List Builder
 */
public class DirListBuilder {
    private final DirTree dirTree;
    private RecyclerView.Adapter adapter;
    private ListDir listDir;
    private MakeDir makeDir;

    public DirListBuilder(Context context, RecyclerView recyclerView) {
        this.dirTree = new DirTree();

        initOps();
        attachRecyclerView(context, recyclerView);
    }

    private void initOps() {
        listDir = new ListDir(dirTree);
        makeDir = new MakeDir(dirTree);
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

    public void processMakeDirEvent(File rootDir, String dirName) {
        makeDir.process(rootDir, dirName);
    }

    public void notifyDatasetChanged() {
        adapter.notifyDataSetChanged();
    }
}
