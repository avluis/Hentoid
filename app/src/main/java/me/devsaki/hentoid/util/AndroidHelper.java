package me.devsaki.hentoid.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;

/**
 * Created by DevSaki on 20/05/2015.
 */
public class AndroidHelper {

    public static void openContent(Content content, final Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        File dir = Helper.getDownloadDir(content, context);

        File imageFile = null;
        File[] files = dir.listFiles();
        Arrays.sort(files);
        for (File file : files) {
            if (file.getName().endsWith(".jpg")) {
                imageFile = file;
                break;
            }
        }
        if (imageFile == null) {
            String message = context.getString(R.string.not_image_file_found).replace("@dir", dir.getAbsolutePath());
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } else {
            int readContentPreference = Integer.parseInt(sharedPreferences.getString(
                    ConstantsPreferences.PREF_READ_CONTENT_LISTS,
                    ConstantsPreferences.PREF_READ_CONTENT_DEFAULT + ""));
            if (readContentPreference == ConstantsPreferences.PREF_READ_CONTENT_ASK) {
                final File file = imageFile;
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setMessage(R.string.select_the_action)
                        .setPositiveButton(R.string.open_default_image_viewer,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        openFile(file, context);
                                    }
                                })
                        .setNegativeButton(R.string.open_perfect_viewer,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        openPerfectViewer(file, context);
                                    }
                                }).create().show();
            } else if (readContentPreference == ConstantsPreferences.PREF_READ_CONTENT_PERFECT_VIEWER) {
                openPerfectViewer(imageFile, context);
            }
        }


    }

    public static void openFile(File aFile, Context context) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        File file = new File(aFile.getAbsolutePath());
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mimetype = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        myIntent.setDataAndType(Uri.fromFile(file), mimetype);

        context.startActivity(myIntent);
    }

    public static void openPerfectViewer(File firstImage, Context context) {
        try {
            Intent intent = context
                    .getPackageManager()
                    .getLaunchIntentForPackage("com.rookiestudio.perfectviewer");
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(firstImage), "image/*");
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.error_open_perfect_viewer,
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static <T> void executeAsyncTask(AsyncTask<T, ?, ?> task,
                                            T... params) {
        task.execute(params);
    }

    public static void ignoreSslErros() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }
}
