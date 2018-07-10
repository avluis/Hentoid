package me.devsaki.hentoid.parsers.mikan;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;

public class MikanAttributeResponse {
    @Expose
    public String request;
    @Expose
    public List<MikanAttribute> result = new ArrayList<>();

    public List<Attribute> toAttributeList()
    {
        List<Attribute> res = new ArrayList<>();

        for (MikanAttribute attr : result)
        {
            res.add(attr.toAttribute());
        }

        return res;
    }
}
