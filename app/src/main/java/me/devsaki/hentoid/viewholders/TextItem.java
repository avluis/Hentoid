package me.devsaki.hentoid.viewholders;

import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ThemeHelper;

import static androidx.core.view.ViewCompat.requireViewById;

public class TextItem<T> extends AbstractItem<TextItem.TextViewHolder> {

    private final String text;
    private final T tag;
    private final boolean centered;

    public TextItem(String text, T tag, boolean centered) {
        this.text = text;
        this.tag = tag;
        this.centered = centered;
    }

    @Nullable
    public T getTag() {
        return tag;
    }

    @NotNull
    @Override
    public TextItem.TextViewHolder<T> getViewHolder(@NotNull View view) {
        return new TextViewHolder<>(view);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_text;
    }

    @Override
    public int getType() {
        return R.id.text;
    }

    static class TextViewHolder<T> extends FastAdapter.ViewHolder<TextItem<T>> {

        private final TextView title;

        TextViewHolder(View view) {
            super(view);
            title = requireViewById(view, R.id.drawer_item_txt);
            int color = ThemeHelper.getColor(view.getContext(), R.color.secondary_light);
            view.setBackground(FastAdapterUIUtils.getSelectablePressedBackground(view.getContext(), FastAdapterUIUtils.adjustAlpha(color, 100), 50, true));
        }


        @Override
        public void bindView(@NotNull TextItem<T> item, @NotNull List<Object> list) {
            title.setText(Helper.capitalizeString(item.text));
            if (item.centered) title.setGravity(Gravity.CENTER);
        }

        @Override
        public void unbindView(@NotNull TextItem item) {

        }
    }
}
