package me.devsaki.hentoid.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.fragments.pin.UnlockPinDialogFragment;
import me.devsaki.hentoid.util.Preferences;

/**
 * This activity asks for a 4 digit pin if it is set and then transitions to another activity
 */
public class UnlockActivity extends BaseActivity implements UnlockPinDialogFragment.Parent {

    private static final String EXTRA_INTENT = "intent";
    private static final String EXTRA_SITE_CODE = "siteCode";

    /**
     * This is reset to false at an undefined time, usually due to process death.
     */
    private static boolean isUnlocked = false;

    /**
     * Creates an intent that launches this activity before launching the given wrapped intent
     *
     * @param context           used for creating the return intent
     * @param destinationIntent intent that refers to the next activity
     * @return intent that launches this activity which leads to another activity referred to by
     * {@code destinationIntent}
     */
    public static Intent wrapIntent(Context context, Intent destinationIntent) {
        Intent intent = new Intent(context, UnlockActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(EXTRA_INTENT, destinationIntent);
        return intent;
    }

    /**
     * Creates an intent that launches this activity before launching the given site's
     * wrapped web activity intent
     * <p>
     * NB : Specific implementation mandatory to create shortcuts because shortcut intents bundles
     * are {@code PersistableBundle}s that cannot only store basic values (no Intent objects)
     *
     * @param context used for creating the return intent
     * @param site    Site whose web activity to launch after the PIN is unlocked
     * @return intent that launches this activity which leads to the {@code site}'s web activity
     */
    public static Intent wrapIntent(Context context, Site site) {
        Intent intent = new Intent(context, UnlockActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(EXTRA_SITE_CODE, site.getCode());
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Preferences.getAppLockPin().length() != 4) {
            Preferences.setAppLockPin("");
        }

        if (Preferences.getAppLockPin().isEmpty() || isUnlocked) {
            goToNextActivity();
            return;
        }

        if (savedInstanceState == null) {
            new UnlockPinDialogFragment().show(getSupportFragmentManager(), null);
        }
    }

    @Override
    public void onPinSuccess() {
        isUnlocked = true;
        goToNextActivity();
    }

    @Override
    public void onPinCancel() {
        finish();
    }

    private void goToNextActivity() {
        Parcelable parcelableExtra = getIntent().getParcelableExtra(EXTRA_INTENT);
        Intent targetIntent;
        if (parcelableExtra != null) targetIntent = (Intent) parcelableExtra;
        else {
            int siteCode = getIntent().getIntExtra(EXTRA_SITE_CODE, Site.NONE.getCode());
            Class c = Content.getWebActivityClass(Site.searchByCode(siteCode));
            targetIntent = new Intent(HentoidApp.getAppContext(), c);
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            targetIntent.setAction(Intent.ACTION_VIEW);
        }
        startActivity(targetIntent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
