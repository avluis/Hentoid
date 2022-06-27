package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import me.devsaki.hentoid.R;

public class MissingWebViewActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_missing_web_view);
    }

    public void onOpenLibraryPressed(View v) {
        onBackPressed();
    }

    @Override
    public void onBackPressed() {
        // from me.devsaki.hentoid.activities.sources.BaseWebActivity.goHome()
        Intent intent = new Intent(this, LibraryActivity.class);
        // If FLAG_ACTIVITY_CLEAR_TOP is not set,
        // it can interfere with Double-Back (press back twice) to exit
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }
}