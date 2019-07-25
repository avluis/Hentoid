package me.devsaki.hentoid.fragments.pin;

import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.security.InvalidParameterException;

import me.devsaki.hentoid.R;

import static androidx.core.view.ViewCompat.requireViewById;

public abstract class PinDialogFragment extends DialogFragment {

    protected abstract void onPinAccept(String pin);

    private final StringBuilder pinValue = new StringBuilder(4);

    private TextView headerText;

    private View placeholderImage1;

    private View placeholderImage2;

    private View placeholderImage3;

    private View placeholderImage4;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.PinEntryDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pin_dialog, container, false);

        headerText = requireViewById(view, R.id.text_header);

        placeholderImage1 = requireViewById(view, R.id.image_placeholder_1);

        placeholderImage2 = requireViewById(view, R.id.image_placeholder_2);

        placeholderImage3 = requireViewById(view, R.id.image_placeholder_3);

        placeholderImage4 = requireViewById(view, R.id.image_placeholder_4);

        View button0 = requireViewById(view, R.id.button_0);
        button0.setOnClickListener(v -> onKeyClick("0"));

        View button1 = requireViewById(view, R.id.button_1);
        button1.setOnClickListener(v9 -> onKeyClick("1"));

        View button2 = requireViewById(view, R.id.button_2);
        button2.setOnClickListener(v8 -> onKeyClick("2"));

        View button3 = requireViewById(view, R.id.button_3);
        button3.setOnClickListener(v7 -> onKeyClick("3"));

        View button4 = requireViewById(view, R.id.button_4);
        button4.setOnClickListener(v6 -> onKeyClick("4"));

        View button5 = requireViewById(view, R.id.button_5);
        button5.setOnClickListener(v5 -> onKeyClick("5"));

        View button6 = requireViewById(view, R.id.button_6);
        button6.setOnClickListener(v4 -> onKeyClick("6"));

        View button7 = requireViewById(view, R.id.button_7);
        button7.setOnClickListener(v3 -> onKeyClick("7"));

        View button8 = requireViewById(view, R.id.button_8);
        button8.setOnClickListener(v2 -> onKeyClick("8"));

        View button9 = requireViewById(view, R.id.button_9);
        button9.setOnClickListener(v1 -> onKeyClick("9"));

        View buttonBackspace = requireViewById(view, R.id.button_backspace);
        buttonBackspace.setOnClickListener(v -> onBackspaceClick());

        return view;
    }

    @Override
    public void onStop() {
        super.onStop();
        getDialog().cancel();
    }

    protected void clearPin() {
        pinValue.setLength(0);
        placeholderImage1.setVisibility(View.INVISIBLE);
        placeholderImage2.setVisibility(View.INVISIBLE);
        placeholderImage3.setVisibility(View.INVISIBLE);
        placeholderImage4.setVisibility(View.INVISIBLE);
    }

    protected void setHeaderText(@StringRes int resId) {
        headerText.setText(resId);
    }

    protected void vibrate() {
        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(300);
        }
    }

    private void onKeyClick(String s) {
        if (pinValue.length() == 4) return;

        pinValue.append(s);
        switch (pinValue.length()) {
            case 1:
                placeholderImage1.setVisibility(View.VISIBLE);
                break;
            case 2:
                placeholderImage2.setVisibility(View.VISIBLE);
                break;
            case 3:
                placeholderImage3.setVisibility(View.VISIBLE);
                break;
            case 4:
                placeholderImage4.setVisibility(View.VISIBLE);
                onPinAccept(pinValue.toString());
                break;
            default:
                throw new InvalidParameterException("Not implemented");
        }
    }

    private void onBackspaceClick() {
        if (pinValue.length() == 0) return;

        pinValue.setLength(pinValue.length() - 1);
        switch (pinValue.length()) {
            case 0:
                placeholderImage1.setVisibility(View.INVISIBLE);
                break;
            case 1:
                placeholderImage2.setVisibility(View.INVISIBLE);
                break;
            case 2:
                placeholderImage3.setVisibility(View.INVISIBLE);
                break;
            case 3:
                placeholderImage4.setVisibility(View.INVISIBLE);
                break;
            default:
                throw new InvalidParameterException("Not implemented");
        }
    }
}
