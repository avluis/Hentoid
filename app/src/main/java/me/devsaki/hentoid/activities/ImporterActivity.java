package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.github.lzyzsd.circleprogress.DonutProgress;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ContentV1;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.v2.bean.DoujinBean;
import me.devsaki.hentoid.v2.bean.URLBean;

/**
 * Provided a directory, takes care of importing existing libraries
 * onto our database.
 */
public class ImporterActivity extends AppCompatActivity {

    private static final String TAG = ImporterActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_importer);

        AndroidHelper.executeAsyncTask(new ImporterAsyncTask());
    }

    class ImporterAsyncTask extends AsyncTask<Integer, String, List<Content>> {

        private List<File> downloadDirs;
        private int currentPercent;
        private DonutProgress donutProgress;
        private TextView tvCurrentStatus;
        private HentoidDB hentoidDB;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            downloadDirs = new ArrayList<>();

            for (Site s : Site.values()) {
                downloadDirs.add(AndroidHelper.getDownloadDir(s, ImporterActivity.this));
            }

            donutProgress = (DonutProgress) findViewById(R.id.donut_progress);
            tvCurrentStatus = (TextView) findViewById(R.id.tvCurrentStatus);
            hentoidDB = new HentoidDB(ImporterActivity.this);
        }

        @Override
        protected void onPostExecute(List<Content> contents) {
            if (contents != null && contents.size() > 0)
                hentoidDB.insertContents(contents.toArray(new Content[contents.size()]));
            Intent intent = new Intent(ImporterActivity.this, DownloadsActivity.class);
            startActivity(intent);
            finish();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            donutProgress.setProgress(currentPercent);
            tvCurrentStatus.setText(values[0]);
        }

        @Override
        protected List<Content> doInBackground(Integer... params) {
            List<Content> contents = null;
            List<File> files = new ArrayList<>();
            for (File downloadDir : downloadDirs) {
                files.addAll(Arrays.asList(downloadDir.listFiles()));
            }
            int processed = 0;
            if (files.size() > 0) {
                contents = new ArrayList<>();
                Date importedDate = new Date();
                for (File file : files) {
                    processed++;
                    currentPercent = (int) (processed * 100.0 / files.size());
                    if (file.isDirectory()) {
                        publishProgress(file.getName());
                        File json = new File(file, Constants.JSON_FILE_NAME_V2);
                        if (json.exists()) {
                            try {
                                Content content = Helper.jsonToObject(json, Content.class);
                                if (content.getStatus() != StatusContent.DOWNLOADED
                                        && content.getStatus() != StatusContent.ERROR)
                                    content.setStatus(StatusContent.MIGRATED);
                                contents.add(content);
                            } catch (Exception e) {
                                Log.e(TAG, "Reading json file", e);
                            }
                        } else {
                            json = new File(file, Constants.JSON_FILE_NAME);
                            if (json.exists()) {
                                try {
                                    ContentV1 content =
                                            Helper.jsonToObject(json, ContentV1.class);
                                    if (content.getStatus() != StatusContent.DOWNLOADED
                                            && content.getStatus() != StatusContent.ERROR)
                                        content.setMigratedStatus();
                                    Content contentV2 = content.toContent();
                                    try {
                                        Helper.saveJson(contentV2, file);
                                    } catch (IOException e) {
                                        Log.e(TAG, "Error Save JSON " + content.getTitle(), e);
                                    }
                                    contents.add(contentV2);
                                } catch (Exception e) {
                                    Log.e(TAG, "Reading json file", e);
                                }
                            } else {
                                json = new File(file, Constants.OLD_JSON_FILE_NAME);
                                if (json.exists()) {
                                    try {
                                        DoujinBean doujinBean =
                                                Helper.jsonToObject(json, DoujinBean.class);
                                        ContentV1 content = new ContentV1();
                                        content.setUrl(doujinBean.getId());
                                        content.setHtmlDescription(doujinBean.getDescription());
                                        content.setTitle(doujinBean.getTitle());
                                        content.setSeries(from(doujinBean.getSeries(),
                                                AttributeType.SERIE));
                                        Attribute artist = from(doujinBean.getArtist(),
                                                AttributeType.ARTIST);
                                        List<Attribute> artists = null;
                                        if (artist != null) {
                                            artists = new ArrayList<>(1);
                                            artists.add(artist);
                                        }
                                        content.setArtists(artists);
                                        content.setCoverImageUrl(doujinBean.getUrlImageTitle());
                                        content.setQtyPages(doujinBean.getQtyPages());
                                        Attribute translator = from(doujinBean.getTranslator(),
                                                AttributeType.TRANSLATOR);
                                        List<Attribute> translators = null;
                                        if (translator != null) {
                                            translators = new ArrayList<>(1);
                                            translators.add(translator);
                                        }
                                        content.setTranslators(translators);
                                        content.setTags(from(doujinBean.getLstTags(),
                                                AttributeType.TAG));
                                        content.setLanguage(from(doujinBean.getLanguage(),
                                                AttributeType.LANGUAGE));

                                        content.setMigratedStatus();
                                        content.setDownloadDate(importedDate.getTime());
                                        Content contentV2 = content.toContent();
                                        try {
                                            Helper.saveJson(contentV2, file);
                                        } catch (IOException e) {
                                            Log.e(TAG, "Error Save JSON " + content.getTitle(), e);
                                        }
                                        contents.add(contentV2);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Reading json file v2", e);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return contents;
        }

        private List<Attribute> from(List<URLBean> urlBeans,
                                     @SuppressWarnings("SameParameterValue") AttributeType type) {
            List<Attribute> attributes = null;
            if (urlBeans == null)
                return null;
            if (urlBeans.size() > 0) {
                attributes = new ArrayList<>();
                for (URLBean urlBean : urlBeans) {
                    Attribute attribute = from(urlBean, type);
                    if (attribute != null)
                        attributes.add(attribute);
                }
            }
            return attributes;
        }

        private Attribute from(URLBean urlBean, AttributeType type) {
            if (urlBean == null) {
                return null;
            }
            try {
                if (urlBean.getDescription() == null) {
                    throw new RuntimeException("Problems loading attribute v2.");
                }
                return new Attribute()
                        .setName(urlBean.getDescription())
                        .setUrl(urlBean.getId())
                        .setType(type);
            } catch (Exception ex) {
                Log.e(TAG, "Parsing urlBean to attribute", ex);
                return null;
            }
        }
    }
}