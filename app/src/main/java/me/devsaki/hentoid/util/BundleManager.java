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
    private static final String KEY_URIS_STR = "urisStr";
    private static final String KEY_CONTENT_ID = "contentId";

    private static final String KEY_REFRESH = "refresh";
    private static final String KEY_REFRESH_RENAME = "rename";
    private static final String KEY_REFRESH_CLEAN_ABSENT = "cleanAbsent";
    private static final String KEY_REFRESH_CLEAN_NO_IMAGES = "cleanNoImages";
    private static final String KEY_REFRESH_CLEAN_UNREADABLE = "cleanUnreadable";


    private final Bundle bundle;

    public BundleManager(Bundle bundle) {
        this.bundle = bundle;
    }

    public BundleManager() {
        this.bundle = new Bundle();
    }


    public Bundle getBundle() {
        return bundle;
    }

    public void setAttributeTypes(AttributeType... attributeTypes) {
        ArrayList<Integer> attrTypes = new ArrayList<>();
        for (AttributeType type : attributeTypes) attrTypes.add(type.getCode());

        bundle.putIntegerArrayList(KEY_ATTRIBUTE_TYPES, attrTypes);
    }

    public List<AttributeType> getAttributeTypes() {
        List<AttributeType> result = new ArrayList<>();

        List<Integer> attrTypesList = bundle.getIntegerArrayList(KEY_ATTRIBUTE_TYPES);
        if (null != attrTypesList && !attrTypesList.isEmpty())
            for (Integer i : attrTypesList) result.add(AttributeType.searchByCode(i));

        return result;
    }


    public void setMode(int mode) {
        bundle.putInt(KEY_MODE, mode);
    }

    public int getMode() {
        return bundle.getInt(KEY_MODE, -1);
    }


    public void setUri(Uri uri) {
        bundle.putString(KEY_URI, uri.toString());
    }

    @Nullable
    public Uri getUri() {
        Uri result = null;

        String uriStr = bundle.getString(KEY_URI, "");
        if (!uriStr.isEmpty()) result = Uri.parse(uriStr);

        return result;
    }

    void setUrisStr(List<String> uris) {
        ArrayList<String> uriList = new ArrayList<>(uris);
        bundle.putStringArrayList(KEY_URIS_STR, uriList);
    }

    @android.support.annotation.Nullable
    public List<String> getUrisStr() {
        return bundle.getStringArrayList(KEY_URIS_STR);
    }


    void setContentId(long contentId) {
        bundle.putLong(KEY_CONTENT_ID, contentId);
    }

    public long getContentId() {
        return bundle.getLong(KEY_CONTENT_ID, 0);
    }


    public void setRefresh(boolean refresh) {
        bundle.putBoolean(KEY_REFRESH, refresh);
    }

    public boolean getRefresh() {
        return bundle.getBoolean(KEY_REFRESH, false);
    }

    public void setRefreshRename(boolean rename) {
        bundle.putBoolean(KEY_REFRESH_RENAME, rename);
    }

    public boolean getRefreshRename() {
        return bundle.getBoolean(KEY_REFRESH_RENAME, false);
    }

    public void setRefreshCleanAbsent(boolean refresh) {
        bundle.putBoolean(KEY_REFRESH_CLEAN_ABSENT, refresh);
    }

    public boolean getRefreshCleanAbsent() {
        return bundle.getBoolean(KEY_REFRESH_CLEAN_ABSENT, false);
    }

    public void setRefreshCleanNoImages(boolean refresh) {
        bundle.putBoolean(KEY_REFRESH_CLEAN_NO_IMAGES, refresh);
    }

    public boolean getRefreshCleanNoImages() {
        return bundle.getBoolean(KEY_REFRESH_CLEAN_NO_IMAGES, false);
    }

    public void setRefreshCleanUnreadable(boolean refresh) {
        bundle.putBoolean(KEY_REFRESH_CLEAN_UNREADABLE, refresh);
    }

    public boolean getRefreshCleanUnreadable() {
        return bundle.getBoolean(KEY_REFRESH_CLEAN_UNREADABLE, false);
    }
}
