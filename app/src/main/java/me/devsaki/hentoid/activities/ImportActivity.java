package me.devsaki.hentoid.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;

import net.rdrei.android.dirchooser.DirectoryChooserFragment;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.v2.bean.URLBean;

/**
 * Created by avluis on 04/02/2016.
 * For test use only.
 */
public class ImportActivity extends AppCompatActivity implements
        DirectoryChooserFragment.OnFragmentInteractionListener {
    private static final String TAG = ImportActivity.class.getName();

    private final static int STORAGE_PERMISSION_RC = 69;
    private DirectoryChooserFragment mDirectoryDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_transparent);

        pickDownloadFolder();
    }

    /**
     * Show the Dialog to select the Download folder
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void pickDownloadFolder() {
        if (ActivityCompat.checkSelfPermission(ImportActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(ImportActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_RC);
            return;
        }

        // Insert Folder Picker instance here
        mDirectoryDialog = DirectoryChooserFragment.newInstance(Constants.DEFAULT_LOCAL_DIRECTORY,
                String.valueOf(android.os.Environment.getExternalStorageDirectory()));

        mDirectoryDialog.show(getFragmentManager(), null);
    }

    @Override
    public void onSelectDirectory(@NonNull String s) {
        // TODO : Save result to Prefs
        mDirectoryDialog.dismiss();

        // TODO: Pass folder path to importer
        // AndroidHelper.executeAsyncTask(new ImportAsyncTask());
    }

    @Override
    public void onCancelChooser() {
        mDirectoryDialog.dismiss();
        Intent returnIntent = new Intent();
        returnIntent.putExtra("result", "RESULT_CANCELED");
        setResult(Activity.RESULT_CANCELED, returnIntent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                pickDownloadFolder();
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // Permission Denied
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("result", "PERMISSION_DENIED");
                    setResult(Activity.RESULT_CANCELED, returnIntent);
                    finish();
                } else {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("result", "PERMISSION_DENIED_FORCED");
                    setResult(Activity.RESULT_CANCELED, returnIntent);
                    finish();
                }
            }
        }
    }

    class ImportAsyncTask extends AsyncTask<Integer, String, List<Content>> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            new MaterialDialog.Builder(getApplicationContext())
                    .title(R.string.progress_dialog)
                    .content(R.string.please_wait)
                    .contentGravity(GravityEnum.CENTER)
                    .progress(false, 150, true)
                    .cancelable(false)
                    .showListener(new DialogInterface.OnShowListener() {
                        @Override
                        public void onShow(DialogInterface dialogInterface) {
                            final MaterialDialog dialog = (MaterialDialog) dialogInterface;

                            // TODO: Hook to dialog here
                        }
                    }).show();

        }

        @Override
        protected List<Content> doInBackground(Integer... params) {
            return null;
        }
    }

    private List<Attribute> from(List<URLBean> urlBeans,
                                 @SuppressWarnings("SameParameterValue") AttributeType type) {
        List<Attribute> attributes = null;
        if (urlBeans == null) {
            return null;
        }
        if (urlBeans.size() > 0) {
            attributes = new ArrayList<>();
            for (URLBean urlBean : urlBeans) {
                Attribute attribute = from(urlBean, type);
                if (attribute != null) {
                    attributes.add(attribute);
                }
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