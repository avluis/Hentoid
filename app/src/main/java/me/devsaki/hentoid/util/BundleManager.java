package me.devsaki.hentoid.util;

import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import me.devsaki.hentoid.enums.AttributeType;

public class BundleManager {

    private static final String KEY_ATTRIBUTE_TYPES = "attributeTypes";
    private static final String KEY_MODE = "mode";
    private static final String KEY_URI = "uri";

    private final Bundle bundle;

    public BundleManager(Bundle bundle)
    {
        this.bundle = bundle;
    }

    public BundleManager()
    {
        this.bundle = new Bundle();
    }


    public Bundle getBundle()
    {
        return bundle;
    }

    public void setAttributeTypes(AttributeType... attributeTypes)
    {
        ArrayList<Integer> attrTypes = new ArrayList<>();
        for (AttributeType type : attributeTypes) attrTypes.add(type.getCode());

        bundle.putIntegerArrayList(KEY_ATTRIBUTE_TYPES, attrTypes);
    }

    public List<AttributeType> getAttributeTypes()
    {
        List<AttributeType> result = new ArrayList<>();

        List<Integer> attrTypesList = bundle.getIntegerArrayList(KEY_ATTRIBUTE_TYPES);
        if (null != attrTypesList && !attrTypesList.isEmpty())
            for (Integer i : attrTypesList) result.add(AttributeType.searchByCode(i));

        return result;
    }


    public void setMode(int mode)
    {
        bundle.putInt(KEY_MODE, mode);
    }

    public int getMode()
    {
        return bundle.getInt(KEY_MODE, -1);
    }


    public void setUri(Uri uri)
    {
        bundle.putString(KEY_URI, uri.toString());
    }

    @Nullable
    public Uri getUri()
    {
        Uri result = null;

        String uriStr = bundle.getString(KEY_URI, "");
        if (!uriStr.isEmpty()) result = Uri.parse(uriStr);

        return result;
    }
}
