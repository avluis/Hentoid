package me.devsaki.hentoid.json;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.enums.Grouping;

class JsonCustomGroup {

    private String name;
    private Integer order;
    private Integer subtype;
    private Boolean favourite;
    private Integer rating;
    private Boolean hasCustomBookOrder;
    private String searchUri;

    private JsonCustomGroup() {
    }

    static JsonCustomGroup fromEntity(Group g) {
        JsonCustomGroup result = new JsonCustomGroup();
        result.name = g.name;
        result.order = g.order;
        result.subtype = g.subtype;
        result.favourite = g.isFavourite();
        result.rating = g.getRating();
        result.hasCustomBookOrder = g.hasCustomBookOrder;
        result.searchUri = g.searchUri;
        return result;
    }

    Group toEntity(@NonNull final Grouping grouping) {
        return new Group(grouping, name, order)
                .setSubtype((null == subtype) ? 0 : subtype)
                .setFavourite(null != favourite && favourite)
                .setRating(null == rating ? 0 : rating)
                .setHasCustomBookOrder(null != hasCustomBookOrder && hasCustomBookOrder)
                .setSearchUri(null == searchUri ? "" : searchUri);
    }
}
