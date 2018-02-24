package me.devsaki.hentoid.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.util.Preferences;

/**
 * If set, this will allow us to 'lock' the app behind a password/code.
 * <p/>
 * TODO: On-Screen virtual keyboard
 */
public class AppLockActivity extends BaseActivity {

    private final long DELAY = 1000;
    private final long[] correctPinPattern = {0, 250, 100, 100};
    private final long[] incorrectPinPattern = {0, 200, 200, 200};
    private final Map<String, Integer> imageMap = new HashMap<>();
    private final String[] LOCK_STATE = {"Locked", "Open"};
    private TextView tvAppLock;
    private EditText etPin;
    private ImageView ivLock;
    private Vibrator vibrator;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_lock);

        imageMap.put(LOCK_STATE[0], R.drawable.ic_lock_closed);
        imageMap.put(LOCK_STATE[1], R.drawable.ic_lock_open);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        tvAppLock = findViewById(R.id.tv_app_lock_subtitle);
        etPin = findViewById(R.id.et_pin);
        ivLock = findViewById(R.id.iv_lock);

        etPin.setOnKeyListener((v, keyCode, event) -> {
            if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                checkPin(null);

                return true;
            }

            return false;
        });
        etPin.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // We don't care about this event.
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

                handler = new Handler();
                handler.postDelayed(() -> {
                    etPin.setText(s.toString());
                    checkPin(null);
                }, DELAY);
            }
        });
    }

    public void checkPin(View view) {
        String pin = etPin.getText().toString();

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (Preferences.getAppLockPin().equals(pin)) {
            etPin.setText("");
            etPin.clearFocus();

            InputMethodManager inputManager =
                    (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(etPin.getWindowToken(), 0);

            tvAppLock.setText(R.string.pin_ok);
            ivLock.setImageResource(imageMap.get(LOCK_STATE[1]));

            invokeVibrate(correctPinPattern);

            Intent intent = new Intent(this, DownloadsActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        } else {
            etPin.selectAll();
            ivLock.setImageResource(imageMap.get(LOCK_STATE[0]));
            tvAppLock.setText(R.string.pin_invalid);

            invokeVibrate(incorrectPinPattern);
        }
    }

    private void invokeVibrate(long[] vibratePattern) {
        if (vibrator.hasVibrator() && Preferences.getAppLockVibrate()) {
            vibrator.vibrate(vibratePattern, -1);
        }
    }
}
