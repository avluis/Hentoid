package me.devsaki.hentoid.json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Content;

public class JsonContentCollection {

    private List<JsonContent> library = new ArrayList<>();
    private List<JsonContent> queue = new ArrayList<>();

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
}
