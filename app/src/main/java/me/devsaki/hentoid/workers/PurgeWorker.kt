package me.devsaki.hentoid.workers

import android.content.Context
import androidx.work.WorkerParameters
import me.devsaki.hentoid.R

class PurgeWorker(context: Context, parameters: WorkerParameters) :
    BaseDeleteWorker(context, R.id.delete_service_purge, parameters)