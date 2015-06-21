package me.devsaki.hentoid;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.widget.TextView;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ContentV1;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.Status;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.v2.bean.DoujinBean;
import me.devsaki.hentoid.v2.bean.URLBean;

import com.github.lzyzsd.circleprogress.DonutProgress;
import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class ImporterActivity extends ActionBarActivity {

    private static final String TAG = ImporterActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_importer);

        AndroidHelper.executeAsyncTask(new ImporterAsyncTask());
    }

    class ImporterAsyncTask extends AsyncTask<Integer, String, List<Content>> {

        private File downloadDir;
        private int currentPercent;
        private DonutProgress donutProgress;
        private TextView tvCurrentStatus;
        private HentoidDB hentoidDB;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            downloadDir = Helper.getDownloadDir("", ImporterActivity.this);

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
            File[] files = downloadDir.listFiles();
            int processeds = 0;
            if (files.length > 0) {
                contents = new ArrayList<>();
                Date importedDate = new Date();
                for (File file : files) {
                    processeds++;
                    currentPercent = (int) (processeds * 100.0 / files.length);
                    if (file.isDirectory()) {
                        publishProgress(file.getName());
                        File json = new File(file, Constants.JSON_FILE_NAME_V2);
                        if (json.exists()) {
                            try {
                                Content content = new Gson().fromJson(Helper.readTextFile(json), Content.class);
                                if (content.getStatus() != me.devsaki.hentoid.database.enums.Status.DOWNLOADED && content.getStatus() != me.devsaki.hentoid.database.enums.Status.ERROR)
                                    content.setStatus(me.devsaki.hentoid.database.enums.Status.MIGRATED);
                                contents.add(content);
                            } catch (Exception e) {
                                Log.e(TAG, "Reading json file", e);
                            }
                        } else {
                            json = new File(file, Constants.JSON_FILE_NAME);
                            if (json.exists()) {
                                try {
                                    ContentV1 content = new Gson().fromJson(Helper.readTextFile(json), ContentV1.class);
                                    if (content.getStatus() != me.devsaki.hentoid.database.enums.Status.DOWNLOADED && content.getStatus() != me.devsaki.hentoid.database.enums.Status.ERROR)
                                        content.setStatus(me.devsaki.hentoid.database.enums.Status.MIGRATED);
                                    contents.add(content.toContent());
                                } catch (Exception e) {
                                    Log.e(TAG, "Reading json file", e);
                                }
                            } else {
                                json = new File(file, Constants.OLD_JSON_FILE_NAME);
                                if (json.exists()) {
                                    try {
                                        DoujinBean doujinBean = new Gson().fromJson(Helper.readTextFile(json), DoujinBean.class);
                                        ContentV1 content = new ContentV1();
                                        content.setUrl(doujinBean.getId());
                                        content.setHtmlDescription(doujinBean.getDescription());
                                        content.setTitle(doujinBean.getTitle());
                                        content.setSerie(from(doujinBean.getSerie(), AttributeType.SERIE));
                                        Attribute artist = from(doujinBean.getArtist(), AttributeType.ARTIST);
                                        List<Attribute> artists = null;
                                        if (artist != null) {
                                            artists = new ArrayList<>(1);
                                            artists.add(artist);
                                        }
                                        content.setArtists(artists);
                                        content.setCoverImageUrl(doujinBean.getUrlImageTitle());
                                        content.setQtyPages(doujinBean.getQtyPages());
                                        Attribute translator = from(doujinBean.getTranslator(), AttributeType.TRANSLATOR);
                                        List<Attribute> translators = null;
                                        if (translator != null) {
                                            translators = new ArrayList<>(1);
                                            translators.add(translator);
                                        }
                                        content.setTranslators(translators);
                                        content.setTags(from(doujinBean.getLstTags(), AttributeType.TAG));
                                        content.setLanguage(from(doujinBean.getLanguage(), AttributeType.LANGUAGE));

                                        content.setStatus(me.devsaki.hentoid.database.enums.Status.MIGRATED);
                                        content.setDownloadDate(importedDate.getTime());
                                        contents.add(content.toContent());
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

        private List<Attribute> from(List<URLBean> urlBeans, AttributeType type) {
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
                Attribute attribute = new Attribute();
                attribute.setName(urlBean.getDescription());
                attribute.setUrl(urlBean.getId());
                attribute.setType(type);
                return attribute;
            } catch (Exception ex) {
                Log.e(TAG, "Parsing urlBean to attribute", ex);
                return null;
            }
        }
    }
}
