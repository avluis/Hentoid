package me.devsaki.hentoid.abstracts;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.AndroidHelper;

/**
 * Created by avluis on 04/13/2016.
 * Abstract Activity for common elements
 */
public abstract class PrimaryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidHelper.setNavBarColor(this, R.color.primary_dark);
    }

    @Override
    public void onBackPressed() {
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        super.onBackPressed();
    }
}