package me.devsaki.hentoid.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;

import com.github.paolorotolo.appintro.AppIntro2;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.fragments.BaseSlide;
import me.devsaki.hentoid.util.AndroidHelper;

/**
 * Created by avluis on 03/20/2016.
 * Welcome activity
 * Presents required permissions, sets storage directory and library import
 * Finish implementing: TODO: File Picker, Library Import
 */
public class IntroSlideActivity extends AppIntro2 {
    private int importSlide = 4;

    @Override
    public void init(@Nullable Bundle savedInstanceState) {
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_01));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addSlide(BaseSlide.newInstance(R.layout.intro_slide_02));
            // Ask Storage permission in the second slide,
            // but only for Android M+ users.
            askForPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
        } else {
            // In order to keep the number of slides the same,
            // we show this info slide for non-M users.
            addSlide(BaseSlide.newInstance(R.layout.intro_slide_02_alt));
        }
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_03));
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_04));
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_05));
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_06));
    }

    @Override
    public void onNextPressed() {
        // Do something when users tap on Next button.
    }

    @Override
    public void onDonePressed() {
        AndroidHelper.commitFirstRun(true);
        AndroidHelper.launchMainActivity(this);
        finish();
    }

    @Override
    public void onSlideChanged() {
        // Show the import dialog just prior to the last slide
        if (pager.getCurrentItem() == importSlide) {
            System.out.println("Is this the right slide?");

            Handler handler = new Handler();

            handler.postDelayed(new Runnable() {

                public void run() {
                    Intent selectFolder = new Intent(
                            getApplicationContext(), SelectFolderActivity2.class);
                    startActivityForResult(selectFolder, 1);
                }
            }, 100);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                // If we get RESULT_OK, then we validate
                // TODO: Implement result validation
                System.out.println("RESULT_OK: ");
                String result = data.getStringExtra("result");
                System.out.println(result);

                /* If result passes validation, then:
                * TODO: If library path contains prior downloads, we import then move to next slide
                * TODO: If library path is clean, then we simply move to next slide
                */

                // Move to next slide
                pager.setCurrentItem(importSlide + 1);
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                System.out.println("RESULT_CANCELED");
                // If we get RESULT_CANCELED, then go back 2 slides
                pager.setCurrentItem(importSlide - 2);
            }

        }
    }
}
