package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.ConstantsPreferences;

/**
 * If set, this will allow us to 'lock' the app behind a password/code.
 */
public class AppLockActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_app_lock);
        setTitle(R.string.title_activity_app_lock);

        final EditText etPin = (EditText) findViewById(R.id.etPin);
        etPin.setGravity(Gravity.CENTER);
        etPin.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN)
                        && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    checkPin(etPin);
                    return true;
                }
                return false;
            }
        });
    }

    @SuppressWarnings("UnusedParameters")
    public void checkPin(View view) {
        String pin = ((EditText) findViewById(R.id.etPin)).getText().toString();
        String appLock = HentoidApplication.getAppPreferences()
                .getString(ConstantsPreferences.PREF_APP_LOCK, "");
        if (appLock.equals(pin)) {
            Intent intent = new Intent(this, DownloadsActivity.class);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, R.string.pin_invalid, Toast.LENGTH_SHORT).show();
        }
    }
}