package me.devsaki.hentoid.dirpicker.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.dirpicker.events.UpdateDirTreeEvent;
import me.devsaki.hentoid.dirpicker.model.DirList;

/**
 * Created by avluis on 06/12/2016.
 * Directory Adapter
 */
public class DirAdapter extends RecyclerView.Adapter<DirAdapter.ViewHolder> {
    private final EventBus bus;
    private final DirList dirList;

    public DirAdapter(DirList dirList, EventBus bus) {
        this.dirList = dirList;
        this.bus = bus;
    }

    @NonNull
    @Override
    public DirAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View root = LayoutInflater.from(
                parent.getContext()).inflate(R.layout.item_dir_picker, parent, false);
        return new ViewHolder(root);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = dirList.get(position);
        holder.textView.setText(file.getName());
    }

    @Override
    public int getItemCount() {
        return dirList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView textView;

        ViewHolder(View root) {
            super(root);

            textView = root.findViewById(R.id.dir_name);
            textView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            bus.post(new UpdateDirTreeEvent(dirList.get(getAdapterPosition())));
        }
    }
}
