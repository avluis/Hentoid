package me.devsaki.hentoid.database.domains;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToMany;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.util.Helper;

@Entity
public class Chapter {

    @Id
    private long id;
    private Integer order = -1;
    private String url = "";
    private String name = "";
    private ToOne<Content> content;
    @Backlink(to = "chapter")
    private ToMany<ImageFile> imageFiles;

    public Chapter() {
    }

    public Chapter(int order, String url, String name) {
        this.order = order;
        this.url = url;
        this.name = name;
    }


    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Integer getOrder() {
        return order;
    }

    public Chapter setOrder(Integer order) {
        this.order = order;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public Chapter setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getName() {
        return name;
    }

    public Chapter setName(String name) {
        this.name = name;
        return this;
    }

    public void setContentId(long contentId) {
        this.content.setTargetId(contentId);
    }

    public ToOne<Content> getContent() {
        return content;
    }

    public void setContent(ToOne<Content> content) {
        this.content = content;
    }

    @Nullable
    public ToMany<ImageFile> getImageFiles() {
        return imageFiles;
    }

    public void setImageFiles(List<ImageFile> imageFiles) {
        // We do want to compare array references, not content
        if (imageFiles != null && imageFiles != this.imageFiles) {
            this.imageFiles.clear();
            this.imageFiles.addAll(imageFiles);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chapter imageFile = (Chapter) o;
        return getId() == imageFile.getId() &&
                Objects.equals(getUrl(), imageFile.getUrl());
    }

    @Override
    public int hashCode() {
        // Must be an int32, so we're bound to use Objects.hash
        return Objects.hash(getId(), getUrl());
    }

    public long uniqueHash() {
        return Helper.hash64((id + "." + url).getBytes());
    }
}
