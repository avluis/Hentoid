package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;

import java.util.ArrayList;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.adapters.GalleryAdapter;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 08/25/2016.
 */
public class GalleryActivity extends BaseActivity {
    private static final String TAG = LogHelper.makeLogTag(GalleryActivity.class);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery_pager);

        Intent intent = getIntent();
        String action = intent.getAction();
        Bundle extras = intent.getExtras() != null ? intent.getExtras() : null;

        ArrayList<String> imageList = new ArrayList<>();
        if (Intent.ACTION_VIEW.equals(action)) {
            LogHelper.d(TAG, "ACTION_VIEW Intent received.");
            if (extras != null) {
                imageList = extras.getStringArrayList(FileHelper.IMAGE_LIST);
            }
        }

        ViewPager viewPager = (ViewPager) findViewById(R.id.image_pager);
        PagerAdapter adapter = new GalleryAdapter(this, imageList);
        viewPager.setAdapter(adapter);
    }
}
