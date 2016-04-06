package me.devsaki.hentoid.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.ConstantsPreferences;

/**
 * If set, this will allow us to 'lock' the app behind a password/code.
 */
public class AppLockActivity extends AppCompatActivity {

    private final long DELAY = 1000;
    private final long[] goodPinPattern = {0, 250, 100, 100};
    private final long[] wrongPinPattern = {0, 200, 200, 200};
    private TextView tvAppLock;
    private EditText etPin;
    private Vibrator vibrator;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_lock);

        AndroidHelper.setNavBarColor(this, "#2b0202");

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        tvAppLock = (TextView) findViewById(R.id.tv_app_lock);
        etPin = (EditText) findViewById(R.id.etPin);
        if (etPin != null) {
            etPin.setGravity(Gravity.CENTER);
            etPin.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                            (keyCode == KeyEvent.KEYCODE_ENTER)) {
                        checkPin(etPin);

                        return true;
                    }

                    return false;
                }
            });
            etPin.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (handler != null) {
                        handler.removeCallbacksAndMessages(null);
                    }
                }

                @Override
                public void afterTextChanged(final Editable s) {
                    if (tvAppLock != null) {
                        tvAppLock.setText(R.string.app_lock_pin);
                    }

                    if (s.length() >= 3) {
                        handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                etPin.setText(s.toString());
                                checkPin(null);
                            }
                        }, DELAY);
                    }
                }
            });
        }
    }

    @SuppressWarnings("UnusedParameters")
    public void checkPin(View view) {
        String pin = etPin.getText().toString();
        String appLock = HentoidApplication.getAppPreferences()
                .getString(ConstantsPreferences.PREF_APP_LOCK, "");

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (appLock.equals(pin)) {
            etPin.setText("");
            tvAppLock.setText(R.string.pin_ok);

            if (vibrator.hasVibrator()) {
                vibrator.vibrate(goodPinPattern, -1);
            }

            Intent intent = new Intent(this, DownloadsActivity.class);
            startActivity(intent);
            finish();
        } else {
            etPin.setText("");
            tvAppLock.setText(R.string.pin_invalid);

            if (vibrator.hasVibrator()) {
                vibrator.vibrate(wrongPinPattern, -1);
            }
        }
    }
}