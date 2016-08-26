package me.devsaki.hentoid.adapters;

import android.app.Activity;
import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;

/**
 * Created by avluis on 08/25/2016.
 */
public class GalleryAdapter extends PagerAdapter {

    private Context context;
    private ArrayList<String> imageList = new ArrayList<>();

    public GalleryAdapter(Context context, ArrayList<String> imageList) {
        this.context = context;
        this.imageList = imageList;
    }

    @Override
    public int getCount() {
        return imageList.size();
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        LayoutInflater inflater = ((Activity) context).getLayoutInflater();

        View view = inflater.inflate(R.layout.item_image, container, false);
        ImageView image = (ImageView) view.findViewById(R.id.image_view);

        HentoidApp.getInstance().loadBitmap(getImage(position), image);

        container.addView(view);

        return view;
    }

    private String getImage(int position) {
        return imageList.get(position);
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }
}
