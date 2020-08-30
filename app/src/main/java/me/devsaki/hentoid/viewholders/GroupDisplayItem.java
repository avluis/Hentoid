package me.devsaki.hentoid.viewholders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.ThemeHelper;

import static androidx.core.view.ViewCompat.requireViewById;
import static me.devsaki.hentoid.util.ImageHelper.tintBitmap;

public class GroupDisplayItem extends AbstractItem<GroupDisplayItem.GroupViewHolder> {

    private static final RequestOptions glideRequestOptions;

    // Group
    private final Group group;
    private final boolean isEmpty;

    static {
        Context context = HentoidApp.getInstance();
        int tintColor = ThemeHelper.getColor(context, R.color.light_gray);

        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_hentoid_trans);
        Drawable d = new BitmapDrawable(context.getResources(), tintBitmap(bmp, tintColor));

        glideRequestOptions = new RequestOptions()
                .centerInside()
                .error(d);
    }

    public GroupDisplayItem(Group group) {
        this.group = group;
        isEmpty = (null == group);
    }


    public Group getGroup() {
        return group;
    }

    @NotNull
    @Override
    public GroupViewHolder getViewHolder(@NotNull View view) {
        return new GroupViewHolder(view);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_library_group;
    }

    @Override
    public int getType() {
        return R.id.group;
    }


    static class GroupViewHolder extends FastAdapter.ViewHolder<GroupDisplayItem> {

        private final View baseLayout;
        private final ImageView ivCover;
        private final TextView title;

        GroupViewHolder(View view) {
            super(view);
            baseLayout = requireViewById(view, R.id.item);
            ivCover = requireViewById(view, R.id.ivCover);
            title = requireViewById(view, R.id.tvTitle);
        }


        @Override
        public void bindView(@NotNull GroupDisplayItem item, @NotNull List<?> list) {

            baseLayout.setVisibility(item.isEmpty ? View.GONE : View.VISIBLE);
            if (item.getGroup() != null && item.getGroup().isBeingDeleted())
                baseLayout.startAnimation(new BlinkAnimation(500, 250));
            else
                baseLayout.clearAnimation();

            if (item.group.picture != null) {
                ImageFile cover = item.group.picture.getTarget();
                if (cover != null) attachCover(cover);
            }
            List<GroupItem> items = item.group.items;
            title.setText(String.format("%s%s", item.group.name, (null == items || items.isEmpty()) ? "" : "(" + items.size() + ")"));
        }

        private void attachCover(ImageFile cover) {
            String thumbLocation = "";
            if (cover.getStatus().equals(StatusContent.DOWNLOADED) || cover.getStatus().equals(StatusContent.MIGRATED) || cover.getStatus().equals(StatusContent.EXTERNAL))
                thumbLocation = cover.getFileUri();
            if (thumbLocation.isEmpty()) thumbLocation = cover.getUrl();

            if (thumbLocation.startsWith("http"))
                Glide.with(ivCover)
                        .load(thumbLocation)
                        .apply(glideRequestOptions)
                        .into(ivCover);
            else
                Glide.with(ivCover)
                        .load(Uri.parse(thumbLocation))
                        .apply(glideRequestOptions)
                        .into(ivCover);
        }

        @Override
        public void unbindView(@NotNull GroupDisplayItem item) {
            // No specific behaviour to implement
        }
    }
}
