package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;

import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.EXTRA_TEXT;

/**
 * Created by avluis on 05/11/2016.
 * Responsible for resolving intents and sending them where appropriate
 * <p>
 * Manages how the app receives a "share" intent
 * e.g. Click a link on reddit - it opens in my browser but I wanna download it in Hentoid
 * => tap share in the browser and select hentoid; that's when IntentActivity takes the lead
 */
public class IntentActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();

        if (ACTION_VIEW.equals(action) && data != null) {
            processIntent(data);
        } else if (ACTION_SEND.equals(action) && intent.hasExtra(EXTRA_TEXT)) {
            processIntent(Uri.parse(intent.getStringExtra(EXTRA_TEXT)));
        } else {
            Timber.d("Unrecognized intent. Cannot process.");
        }

        finish();
    }

    private void processIntent(Uri data) {
        Timber.d("Uri: %s", data);

        Site site = Site.searchByUrl(data.getHost());
        if (site == null) {
            Timber.d("Unrecognized site");
            return;
        }

        String parsedPath = parsePath(site, data);
        if (parsedPath == null) {
            Timber.d("Cannot parse path");
            return;
        }

        Content content = new Content();
        content.setSite(site);
        content.setUrl(parsedPath);
        Helper.viewContent(this, content, true);
    }

    @Nullable
    private static String parsePath(Site site, Uri data) {
        String toParse = data.getPath();
        if (null == toParse) return null;

        switch (site) {
            case HITOMI:
                return toParse.replace("/galleries", "");
            case NHENTAI:
                return toParse.replace("/g", "");
            case TSUMINO:
                return toParse.replace("/Book/Info", "");
            case ASMHENTAI_COMICS:
                return toParse.replace("/g", "") + "/"; // '/' required
            case ASMHENTAI:
                return toParse.replace("/g", "") + "/"; // '/' required
            case HENTAICAFE:
                String path = data.toString();
                return path.contains("/?p=") ? path.replace(Site.HENTAICAFE.getUrl(), "") : toParse;
            case PURURIN:
                return toParse.replace("/gallery", "") + "/";
            default:
                return null;
        }
    }
}
