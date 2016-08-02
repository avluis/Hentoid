package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;

import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;

import static me.devsaki.hentoid.util.Helper.DURATION.LONG;

/**
 * Created by avluis on 05/11/2016.
 * Responsible for resolving intents and sending them where appropriate
 */
public class IntentActivity extends BaseActivity {
    private static final String TAG = LogHelper.makeLogTag(IntentActivity.class);

    private static final String HITOMI = "hitomi.la";
    private static final String NHENTAI = "nhentai.net";
    private static final String TSUMINO = "www.tsumino.com";
    private static final String ASMHENTAI = "asmhentai.com";
    private static final String HENTAICAFE = "hentai.cafe";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Bundle extras = intent.getExtras() != null ? intent.getExtras() : null;

        Site site = null;
        String parsedString = null;
        String toParse;
        Uri data = null;

        if (Intent.ACTION_VIEW.equals(action)) {
            LogHelper.d(TAG, "ACTION_VIEW Intent received.");
            data = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action) & type != null & extras != null) {
            LogHelper.d(TAG, "ACTION_SEND Intent received.");
            data = Uri.parse(intent.getStringExtra(Intent.EXTRA_TEXT));
        }

        if (data != null) {
            LogHelper.d(TAG, "Uri: " + data);
            toParse = data.getPath();

            switch (data.getHost()) {
                case HITOMI:
                    site = Site.HITOMI;
                    parsedString = toParse.replace("/galleries", "");
                    break;
                case NHENTAI:
                    site = Site.NHENTAI;
                    parsedString = toParse.replace("/g", "");
                    break;
                case TSUMINO:
                    site = Site.TSUMINO;
                    parsedString = toParse.replace("/Book/Info", "");
                    break;
                case ASMHENTAI:
                    site = Site.ASMHENTAI;
                    parsedString = toParse.replace("/g", "").concat("/"); // '/' required
                    break;
                case HENTAICAFE:
                    site = Site.HENTAICAFE;
                    String path = data.toString();
                    parsedString = path.contains("/?p=") ? path.replace(Site.HENTAICAFE.getUrl(),
                            "") : toParse;
                    break;
                default:
                    LogHelper.d(TAG, "Unknown host!");
                    site = null;
                    break;
            }
        }

        if (site != null) {
            Content content = new Content();
            content.setSite(site);
            content.setUrl(parsedString);
            Helper.viewContent(this, content);
        } else {
            Helper.toast(this, "Can't do anything with this, sorry!", LONG);
        }

        finish();
    }
}
