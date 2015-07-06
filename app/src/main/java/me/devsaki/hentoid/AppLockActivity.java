package me.devsaki.hentoid;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import me.devsaki.hentoid.util.ConstantsPreferences;


public class AppLockActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_lock);
        setTitle(R.string.title_activity_app_lock);
        final String appLock = PreferenceManager.getDefaultSharedPreferences(this).getString(ConstantsPreferences.PREF_APP_LOCK, "");
        if (appLock.isEmpty()) {
            finish();
            Intent intent = new Intent(this, DownloadsActivity.class);
            startActivity(intent);
        }

        final EditText etPin = (EditText) findViewById(R.id.etPin);
        etPin.setGravity(Gravity.CENTER);
        etPin.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    checkPin(etPin);
                    return true;
                }
                return false;
            }
        });
    }

    public void checkPin(View view) {
        String pin = ((EditText) findViewById(R.id.etPin)).getText().toString();
        String appLock = PreferenceManager.getDefaultSharedPreferences(this).getString(ConstantsPreferences.PREF_APP_LOCK, "");
        if (appLock.equals(pin)) {
            finish();
            Intent intent = new Intent(this, DownloadsActivity.class);
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.pin_invalid, Toast.LENGTH_SHORT).show();
        }
    }
}
