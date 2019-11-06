package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;

import static androidx.core.view.ViewCompat.requireViewById;

public class AttributeViewHolder extends RecyclerView.ViewHolder {

    private final TextView view;

    public AttributeViewHolder(View itemView) {
        super(itemView);
        view = requireViewById(itemView, R.id.attributeChip);
    }

    public void bindTo(Attribute attribute) {
        view.setText(attribute.formatLabel());
        view.setTag(attribute);
    }
}
