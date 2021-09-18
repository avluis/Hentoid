package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import me.devsaki.hentoid.R;

public class PurgeWorker extends BaseDeleteWorker {
    public PurgeWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, R.id.delete_service_purge, parameters);
    }
}
