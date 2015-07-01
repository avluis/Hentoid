package me.devsaki.hentoid;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.webkit.WebView;

import me.devsaki.hentoid.util.ConstantsPreferences;

public class MessageSupportActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_support);

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(this);
            prefs.edit().putBoolean(ConstantsPreferences.SHOW_MESSAGE_SUPPORT + pInfo.versionCode, false).apply();
        } catch (Exception ex) {
        }

        WebView wvHelp = (WebView) findViewById(R.id.wbSupport);
        wvHelp.loadDataWithBaseURL(null, getResources().getString(R.string.support_html), "text/html", "utf-8", null);
    }

    public void close(View view) {
        finish();
    }
}
