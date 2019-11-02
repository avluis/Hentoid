package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;

public class AttributeViewHolder extends RecyclerView.ViewHolder {

    private final TextView view;

    public AttributeViewHolder(View itemView) {
        super(itemView);
        view = itemView.findViewById(R.id.attributeChip);
    }

    public void bindTo(Attribute attribute, boolean useNamespace) {
        view.setText(attribute.formatLabel(useNamespace));
        view.setTag(attribute);
    }
}
