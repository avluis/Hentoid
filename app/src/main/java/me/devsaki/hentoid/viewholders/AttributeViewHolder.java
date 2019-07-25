package me.devsaki.hentoid.viewholders;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;

public class AttributeViewHolder extends RecyclerView.ViewHolder {

    private final TextView view;

    public AttributeViewHolder(View itemView) {
        super(itemView);
        view = itemView.findViewById(R.id.attributeChip);
    }

    public void bindTo(Attribute attribute) {
        view.setText(attribute.formatLabel());
        view.setTag(attribute);
    }
}
