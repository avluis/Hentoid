package me.devsaki.hentoid.abstracts;

import androidx.appcompat.app.AppCompatActivity;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.ToastUtil;

/**
 * Created by avluis on 04/13/2016.
 * Abstract Activity for common elements
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        ToastUtil.cancelToast();
    }
}
