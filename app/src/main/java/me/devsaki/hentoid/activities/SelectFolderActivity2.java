package me.devsaki.hentoid.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import me.devsaki.hentoid.R;

/**
 * Presents the user with a dialog to pick save directory
 * TODO: Currently a stub class for testing
 * Created by avluis on 03/27/2016.
 */
public class SelectFolderActivity2 extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_folder_2);

        View selectFolder = findViewById(R.id.select_folder_dialog);

        if (selectFolder != null) {
            Snackbar snackbar = Snackbar
                    .make(selectFolder, "Nothing here, closing in 10", Snackbar.LENGTH_LONG);

            snackbar.show();
        }

        Handler handler = new Handler();

        handler.postDelayed(new Runnable() {
            private static final String result = "Not everything in life is...";

            public void run() {
                Intent returnIntent = new Intent();
                returnIntent.putExtra("result", result);
                // setResult(Activity.RESULT_OK, returnIntent);
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        }, 10000);
    }
}
