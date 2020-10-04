package me.devsaki.hentoid.json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.enums.Grouping;

public class JsonContentCollection {

    private List<JsonContent> library = new ArrayList<>();
    private List<JsonContent> queue = new ArrayList<>();
    private List<JsonCustomGrouping> groupings = new ArrayList<>();

    public JsonContentCollection() {
        // Nothing special do to here
    }

    public List<Content> getLibrary(@Nullable CollectionDAO dao) {
        return Stream.of(library).map(c -> c.toEntity(dao)).toList();
    }

    public void setLibrary(@NonNull List<Content> library) {
        this.library = Stream.of(library).map(c -> JsonContent.fromEntity(c, false)).toList();
    }

    public List<Content> getQueue() {
        return Stream.of(queue).map(c -> c.toEntity(null)).toList();
    }

    public void setQueue(@NonNull List<Content> queue) {
        this.queue = Stream.of(queue).map(c -> JsonContent.fromEntity(c, false)).toList();
    }

    public List<Group> getCustomGroups() {
        return Stream.of(groupings).flatMap(gr -> Stream.of(gr.getGroups())).map(g -> g.toEntity(Grouping.CUSTOM)).toList();
    }

    public void setCustomGroups(@NonNull List<Group> customGroups) {
        this.groupings = new ArrayList<>();
        this.groupings.add(JsonCustomGrouping.fromEntity(Grouping.CUSTOM, customGroups)); // Just one for now
    }
}
