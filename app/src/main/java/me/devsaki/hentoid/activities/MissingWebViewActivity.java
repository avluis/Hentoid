package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.view.View;

import me.devsaki.hentoid.R;

public class MissingWebViewActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_missing_web_view);
    }

    public void onCloseHentoidPressed(View v) {
        System.exit(0);
    }
}