package me.devsaki.hentoid.util;

import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

public class UrlBuilder {

    private String site;
    private List<Pair<String,String>> params = new ArrayList<>();


    public UrlBuilder(String site) { setSite(site); }


    public void setSite(String site) { this.site = site; }
    public void addParam(String name, String value)
    {
        params.add(new Pair<>(name, value));
    }
    public void addParam(String name, int value)
    {
        params.add(new Pair<>(name, value+""));
    }
    public void addParam(String name, boolean value)
    {
        params.add(new Pair<>(name, value?"true":"false"));
    }

    public String toString()
    {
        StringBuilder url = new StringBuilder(site);
        if (params.size() > 0)
        {
            url.append("?");
            boolean first = true;
            for(Pair<String,String> p : params) {
                if (first) first = false; else url.append("&");
                url.append(p.first).append("=").append(p.second);
            }
        }
        return url.toString();
    }
}
