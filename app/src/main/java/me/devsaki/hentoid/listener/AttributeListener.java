package me.devsaki.hentoid.listener;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;

public interface AttributeListener {
    void onAttributesReady(List<Attribute> attributeList, int totalAttributes);

    void onAttributesFailed();
}

