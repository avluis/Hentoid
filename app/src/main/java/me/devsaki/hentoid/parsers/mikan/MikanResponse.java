package me.devsaki.hentoid.parsers.mikan;

import com.google.gson.annotations.Expose;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

public class MikanResponse implements Serializable {

    @Expose
    public String request;
    @Expose
    public int maxpage;
    @Expose
    public List<String> pages = new ArrayList<>();
    @Expose
    public List<MikanContent> result = new ArrayList<>();


    public List<Content> toContentList()
    {
        List<Content> res = new ArrayList<>();

        for (MikanContent mikanContent : result)
        {
            if (mikanContent.url != null) res.add(mikanContent.toContent());
        }

        return res;
    }

    public List<ImageFile> toImageFileList()
    {
        int i=0;
        List<ImageFile> res = new ArrayList<>();

        for (String s : pages)
        {
            res.add(new ImageFile(i, s, StatusContent.ONLINE));
            i++;
        }

        return res;
    }
}
