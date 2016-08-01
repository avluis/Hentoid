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
        Uri data = intent.getData();

        Site site;
        String parsedString = null;
        String stringToParse = data.getPath();

        switch (data.getHost()) {
            case HITOMI:
                site = Site.HITOMI;
                parsedString = stringToParse.replace("/galleries", "");
                break;
            case NHENTAI:
                site = Site.NHENTAI;
                parsedString = stringToParse.replace("/g", "");
                break;
            case TSUMINO:
                site = Site.TSUMINO;
                parsedString = stringToParse.replace("/Book/Info", "");
                break;
            case ASMHENTAI:
                site = Site.ASMHENTAI;
                parsedString = stringToParse.replace("/g", "").concat("/");
                break;
            case HENTAICAFE:
                site = Site.HENTAICAFE;
                parsedString = stringToParse;
                break;
            default:
                LogHelper.d(TAG, "Unknown host!");
                site = null;
                break;
        }

        if (site != null) {
            Content content = new Content();
            content.setSite(site);
            content.setUrl(parsedString);

            Helper.viewContent(this, content);
        } else {
            Helper.toast(this, "Can't do anything with this, sorry!");
        }
        finish();
    }
}
