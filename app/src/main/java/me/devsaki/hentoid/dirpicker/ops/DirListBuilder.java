package me.devsaki.hentoid.dirpicker.ops;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import me.devsaki.hentoid.dirpicker.adapter.DirAdapter;
import me.devsaki.hentoid.dirpicker.events.DataSetChangedEvent;
import me.devsaki.hentoid.dirpicker.events.OnMakeDirEvent;
import me.devsaki.hentoid.dirpicker.events.UpdateDirTreeEvent;
import me.devsaki.hentoid.dirpicker.model.DirTree;

/**
 * Created by avluis on 06/12/2016.
 * Directory List Builder
 */
public class DirListBuilder {
    private final DirTree dirTree;
    private final EventBus bus;
    private RecyclerView.Adapter adapter;
    private ListDir listDir;
    private MakeDir makeDir;

    public DirListBuilder(Context context, EventBus bus, RecyclerView recyclerView) {
        this.bus = bus;
        this.dirTree = new DirTree(bus);

        initOps();
        attachRecyclerView(context, recyclerView);
    }

    private void initOps() {
        listDir = new ListDir(dirTree, bus);
        makeDir = new MakeDir(dirTree, bus);
    }

    private void attachRecyclerView(Context context, RecyclerView recyclerView) {
        adapter = new DirAdapter(dirTree.dirList, bus);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);
    }

    @Subscribe
    public void onUpdateDirTreeEvent(UpdateDirTreeEvent event) {
        listDir.process(event.rootDir);
    }

    @Subscribe
    public void onMakeDirEvent(OnMakeDirEvent event) {
        makeDir.process(event.root, event.dirName);
    }

    @Subscribe
    public void onDataSetChangedEvent(DataSetChangedEvent event) {
        adapter.notifyDataSetChanged();
    }
}
