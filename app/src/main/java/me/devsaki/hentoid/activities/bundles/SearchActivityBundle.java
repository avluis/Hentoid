package me.devsaki.hentoid.activities.bundles;

import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.database.domains.AttributeMap;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.activities.SearchActivity}
 * through a Bundle.
 *
 * Use Builder class to set data; use Parser class to get data
 */
public class SearchActivityBundle {
    private static final String KEY_ATTRIBUTE_TYPES = "attributeTypes";
    private static final String KEY_MODE = "mode";
    private static final String KEY_URI = "uri";
    private static final String KEY_GROUP = "group";
    private static final String EXCLUDE_MODE = "exclude";

    private SearchActivityBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setAttributeTypes(AttributeType... attributeTypes) {
            ArrayList<Integer> attrTypes = new ArrayList<>();
            for (AttributeType type : attributeTypes) attrTypes.add(type.getCode());

            bundle.putIntegerArrayList(KEY_ATTRIBUTE_TYPES, attrTypes);
        }
        public void setExcludeMode(boolean excludeMode){
            bundle.putBoolean(EXCLUDE_MODE, excludeMode);
        }

        public void setMode(int mode) {
            bundle.putInt(KEY_MODE, mode);
        }

        public void setGroup(long group) {
            bundle.putLong(KEY_GROUP, group);
        }

        public Builder setUri(Uri uri) {
            bundle.putString(KEY_URI, uri.toString());
            return this;
        }

        public static Uri buildSearchUri(List<Attribute> attributes) {
            AttributeMap metadataMap = new AttributeMap();
            metadataMap.addAll(attributes);

            Uri.Builder searchUri = new Uri.Builder()
                    .scheme("search")
                    .authority("hentoid");


            for (Map.Entry<AttributeType, List<Attribute>> entry : metadataMap.entrySet()) {
                AttributeType attrType = entry.getKey();
                List<Attribute> attrs = entry.getValue();
                if (attrs != null)
                    for (Attribute attr : attrs)
                        searchUri.appendQueryParameter(attrType.name(), attr.getId() + ";" + attr.getName() + ";" + attr.isExcluded());
            }

            return searchUri.build();
        }

        public Bundle getBundle() {
            return bundle;
        }
    }

    public static final class Parser {

        private final Bundle bundle;

        public Parser(@Nonnull Bundle bundle) {
            this.bundle = bundle;
        }

        public List<AttributeType> getAttributeTypes() {
            List<AttributeType> result = new ArrayList<>();

            List<Integer> attrTypesList = bundle.getIntegerArrayList(KEY_ATTRIBUTE_TYPES);
            if (null != attrTypesList && !attrTypesList.isEmpty())
                for (Integer i : attrTypesList) result.add(AttributeType.searchByCode(i));

            return result;
        }
        public boolean getExcludeMode(){return bundle.getBoolean(EXCLUDE_MODE, false);}
        public int getMode() {
            return bundle.getInt(KEY_MODE, -1);
        }

        public long getGroupId() {
            return bundle.getLong(KEY_GROUP, -1);
        }

        @Nullable
        public Uri getUri() {
            Uri result = null;

            String uriStr = bundle.getString(KEY_URI, "");
            if (!uriStr.isEmpty()) result = Uri.parse(uriStr);

            return result;
        }

        public static List<Attribute> parseSearchUri(Uri uri) {
            List<Attribute> result = new ArrayList<>();

            if (uri != null)
                for (String typeStr : uri.getQueryParameterNames()) {
                    AttributeType type = AttributeType.searchByName(typeStr);
                    if (type != null)
                        for (String attrStr : uri.getQueryParameters(typeStr)) {
                            String[] attrParams = attrStr.split(";");
                            if (3 == attrParams.length) {
                                result.add(new Attribute(type, attrParams[1]).setId(Long.parseLong(attrParams[0])).setExcluded(Boolean.parseBoolean(attrParams[2])));
                            }
                        }
                }

            return result;
        }
    }


}
