package me.devsaki.hentoid.services;

import android.content.Context;

import com.android.volley.Request;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ContentParser;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.util.FileHelper;
import timber.log.Timber;

public class ContentDownload {

    public static void downloadContent(Context context, Content content)
    {
        HentoidDB db = HentoidDB.getInstance(context);

        content.setDownloadDate(new Date().getTime())
                .setStatus(StatusContent.DOWNLOADING);

        db.updateContentStatus(content);

        List<ImageFile> images = parseImageFiles(content);
        content.setImageFiles(images);
        db.insertImageFiles(content);

        File dir = FileHelper.getContentDownloadDir(context, content);
        Timber.d("Directory created: %s", FileHelper.createDirectory(dir));

        for(ImageFile img : images)
        {
            QueueSingleton.getInstance(context).addToRequestQueue(buildStringRequest(img, dir));
        }
    }

    private static List<ImageFile> parseImageFiles(Content content) {
        ContentParser parser = ContentParserFactory.getInstance().getParser(content);
        List<String> aUrls = parser.parseImageList(content);

        int i = 1;
        List<ImageFile> imageFileList = new ArrayList<>();
        for (String str : aUrls) {
            String name = String.format(Locale.US, "%03d", i);
            imageFileList.add(new ImageFile()
                    .setUrl(str)
                    .setOrder(i++)
                    .setStatus(StatusContent.SAVED)
                    .setName(name));
        }

        return imageFileList;
    }

    private static InputStreamVolleyRequest buildStringRequest(ImageFile img, File dir)
    {
        return new InputStreamVolleyRequest(Request.Method.GET, img.getUrl(),
                response -> {
                    // TODO handle the response
                    try {
                        Timber.d("xxxResponse %s", img.getUrl());
                        if (response!=null) {
                            //covert reponse to input stream
                            InputStream input = new ByteArrayInputStream(response);

                            //Create a file on desired path and write stream data to it
                            File file = new File(dir, Math.random()+".jpg");
//                                map.put("resume_path", file.toString());
                            Timber.d("xxxWriteTo %s",file.getPath());
                            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file));
                            byte data[] = new byte[1024];

                            long total = 0;
                            int count;

                            while ((count = input.read(data)) != -1) {
                                total += count;
                                output.write(data, 0, count);
                            }

                            output.flush();

                            output.close();
                            input.close();
                        }
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        Timber.d("KEY_ERROR", "UNABLE TO DOWNLOAD FILE");
                        e.printStackTrace();
                    }
                }, error -> {
                    // TODO handle the error
            Timber.d("xxxError");
                    error.printStackTrace();
                }, null);
    }
}
