package me.devsaki.hentoid.core;

import android.content.Context;
import android.content.Intent;
import android.os.Process;

import androidx.annotation.NonNull;

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
        Intent intent = new Intent(myContext, myActivityClass);
        myContext.startActivity(intent);
        // Restart the Activity
        Process.killProcess(Process.myPid());
        System.exit(0);
    }
}
