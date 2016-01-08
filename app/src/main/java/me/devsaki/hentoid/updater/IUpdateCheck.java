package me.devsaki.hentoid.updater;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Created by avluis on 8/21/15.
 */
public interface IUpdateCheck {
    void checkForUpdate(final Context context,
                        final boolean onlyWifi, final boolean showToast,
                        final UpdateCheck.UpdateCheckCallback updateCheckResult);

    int getAppVersionCode(final Context context) throws PackageManager.NameNotFoundException;
}