package me.devsaki.hentoid.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

/**
 * Created by avluis on 04/17/2016.
 * Custom implementation of Simple Adapter for our TextView compound drawable.
 */
public class CompoundAdapter extends SimpleAdapter implements SimpleAdapter.ViewBinder {

    private final LayoutInflater mInflater;
    private final int mResource;
    private final String[] mFrom;
    private final int[] mTo;
    private final List<? extends Map<String, ?>> mData;
    private ViewBinder mViewBinder;

    protected CompoundAdapter(Context context, List<? extends Map<String, ?>> data, int resource,
                              String[] from, int[] to) {
        super(context, data, resource, from, to);

        mResource = resource;
        mFrom = from;
        mTo = to;
        mData = data;

        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    private View createViewFromResource(LayoutInflater inflater, int position, View convertView,
                                        ViewGroup parent, int resource) {
        View v;
        if (convertView == null) {
            v = inflater.inflate(resource, parent, false);
        } else {
            v = convertView;
        }

        bindView(position, v);

        return v;
    }

    private void bindView(int position, View view) {
        final Map dataSet = mData.get(position);
        if (dataSet == null) {
            return;
        }

        final ViewBinder binder = mViewBinder;
        final int[] to = mTo;
        final int count = to.length;

        for (int i = 0; i < count; i++) {
            final View v = view.findViewById(to[i]);
            if (v != null) {
                final Object data = dataSet.get(mFrom[i]);
                String IMAGE_KEY = DrawerMenuContents.FIELD_ICON;
                final Object imageData = dataSet.get(IMAGE_KEY);
                String text = data == null ? "" : data.toString();

                int resourceId = (Integer) imageData;

                if (text == null) {
                    text = "";
                }

                boolean bound = false;
                if (binder != null) {
                    bound = binder.setViewValue(v, data, text);
                }

                if (!bound) {
                    setViewText((TextView) v, text);
                    setViewDrawable((TextView) v, resourceId);
                }
            }
        }
    }

    private void setViewDrawable(TextView view, int resource) {
        view.setCompoundDrawablesWithIntrinsicBounds(resource, 0, 0, 0);
    }

    @Override
    public ViewBinder getViewBinder() {
        return mViewBinder;
    }

    @Override
    public void setViewBinder(ViewBinder viewBinder) {
        mViewBinder = viewBinder;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(mInflater, position, convertView, parent, mResource);
    }

    @Override
    public boolean setViewValue(View view, Object data, String textRepresentation) {
        return true;
    }
}
