package me.devsaki.hentoid.core;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.content.Context;
import android.content.Intent;
import android.os.Process;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

public class EmergencyRestartHandler implements
        java.lang.Thread.UncaughtExceptionHandler {
    private final Context myContext;
    private final Class<?> myActivityClass;

    public EmergencyRestartHandler(Context context, Class<?> c) {
        myContext = context;
        myActivityClass = c;
    }

    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable exception) {
        Timber.e(exception);

        // Log the exception
        Timber.i("Logging crash exception");
        try {
            Helper.logException(exception);
        } finally {
            // Restart the Activity
            Timber.i("Restart %s", myActivityClass.getSimpleName());
            Intent intent = new Intent(myContext, myActivityClass);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_NEW_TASK);
            myContext.startActivity(intent);
        }

        Timber.i("Kill current process");
        Process.killProcess(Process.myPid());
        System.exit(0);
    }
}
