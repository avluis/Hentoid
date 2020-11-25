package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.IntDef;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import me.devsaki.hentoid.R;

import static androidx.core.view.ViewCompat.requireViewById;

public class GitHubReleaseDescItem extends AbstractItem<GitHubReleaseDescItem.ReleaseDescriptionViewHolder> {

    @IntDef({Type.DESCRIPTION, Type.LIST_ITEM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
        int DESCRIPTION = 0;
        int LIST_ITEM = 1;
    }

    private final String text;
    private final @Type
    int entryType;

    GitHubReleaseDescItem(String text, @Type int entryType) {
        this.text = text;
        this.entryType = entryType;
    }

    @NotNull
    @Override
    public ReleaseDescriptionViewHolder getViewHolder(@NotNull View view) {
        return new ReleaseDescriptionViewHolder(view);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_text;
    }

    @Override
    public int getType() {
        return R.id.github_release_description;
    }

    static class ReleaseDescriptionViewHolder extends FastAdapter.ViewHolder<GitHubReleaseDescItem> {

        private final int LINE_PADDING;
        private final TextView title;

        ReleaseDescriptionViewHolder(View view) {
            super(view);
            title = requireViewById(view, R.id.item_txt);
            LINE_PADDING = (int) view.getResources().getDimension(R.dimen.changelog_line_padding);
        }


        @Override
        public void bindView(@NotNull GitHubReleaseDescItem item, @NotNull List<?> list) {
            if (item.entryType == Type.DESCRIPTION) setDescContent(item.text);
            else if (item.entryType == Type.LIST_ITEM) setListContent(item.text);
        }

        void setDescContent(String text) {
            title.setText(text);
            title.setPadding(0, LINE_PADDING, 0, 0);
        }

        void setListContent(String text) {
            title.setText(text);
            title.setPadding(LINE_PADDING * 2, LINE_PADDING, 0, 0);
        }

        @Override
        public void unbindView(@NotNull GitHubReleaseDescItem item) {
            // No specific behaviour to implement
        }
    }
}
