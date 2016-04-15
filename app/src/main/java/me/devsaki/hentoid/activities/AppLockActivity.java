package me.devsaki.hentoid.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.PrimaryActivity;
import me.devsaki.hentoid.util.ConstantsPreferences;

/**
 * If set, this will allow us to 'lock' the app behind a password/code.
 */
public class AppLockActivity extends PrimaryActivity {

    private final long DELAY = 1000;
    private final long[] goodPinPattern = {0, 250, 100, 100};
    private final long[] wrongPinPattern = {0, 200, 200, 200};
    private TextView tvAppLock;
    private EditText etPin;
    private ImageView ivLock;
    private Vibrator vibrator;
    private Handler handler = new Handler();
    private Map<String, Integer> imageMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_lock);

        imageMap.put("Locked", R.drawable.ic_lock_closed);
        imageMap.put("Open", R.drawable.ic_lock_open);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        tvAppLock = (TextView) findViewById(R.id.tv_app_lock_subtitle);
        etPin = (EditText) findViewById(R.id.et_pin);
        ivLock = (ImageView) findViewById(R.id.iv_lock);

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

                    if (s.length() >= 4) {
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
            etPin.clearFocus();

            tvAppLock.setText(R.string.pin_ok);
            ivLock.setImageResource(imageMap.get("Open"));

            if (vibrator.hasVibrator()) {
                vibrator.vibrate(goodPinPattern, -1);
            }

            Intent intent = new Intent(this, DownloadsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            ivLock.setImageResource(imageMap.get("Locked"));
            tvAppLock.setText(R.string.pin_invalid);

            if (vibrator.hasVibrator()) {
                vibrator.vibrate(wrongPinPattern, -1);
            }
        }
    }
}