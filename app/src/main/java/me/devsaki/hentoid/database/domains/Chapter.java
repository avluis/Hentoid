package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToMany;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

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
    private String uniqueId = "";


    public Chapter() { // Required by ObjectBox when an alternate constructor exists
    }

    public Chapter(int order, String url, String name) {
        this.order = order;
        this.url = url;
        this.name = name;
    }

    public static Chapter fromChapter(Chapter chap) {
        return new Chapter(chap.order, chap.url, chap.name).setUniqueId(chap.uniqueId);
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

    public String getUniqueId() {
        return (null == uniqueId) ? "" : uniqueId;
    }

    public Chapter setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
        return this;
    }

    public Chapter setContentId(long contentId) {
        this.content.setTargetId(contentId);
        return this;
    }

    public ToOne<Content> getContent() {
        return content;
    }

    public void setContent(ToOne<Content> content) {
        this.content = content;
    }

    public void setContent(Content content) {
        if (null == this.content) {
            Timber.d(">> INIT ToONE");
            this.content = new ToOne<>(this, Chapter_.content);
        }
        this.content.setTarget(content);
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

    public void removeImageFile(ImageFile img) {
        if (imageFiles != null) imageFiles.remove(img);
    }

    public void addImageFile(ImageFile img) {
        if (imageFiles != null) imageFiles.add(img);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chapter chapter = (Chapter) o;
        return getId() == chapter.getId() &&
                Objects.equals(getOrder(), chapter.getOrder()) &&
                Objects.equals(getUrl(), chapter.getUrl());
    }

    @Override
    public int hashCode() {
        // Must be an int32, so we're bound to use Objects.hash
        return Objects.hash(getId(), getOrder(), getUrl());
    }

    public long uniqueHash() {
        return Helper.hash64((id + "." + order + "." + url).getBytes());
    }

    // Obliged to do that until minApi becomes 24 (-> use Comparator.comparing / Comparator.thenComparingLong)
    public static class OrderComparator implements Comparator<Chapter> {
        @Override
        public int compare(@NonNull Chapter o1, @NonNull Chapter o2) {
            int sComp = o1.getOrder().compareTo(o2.order);
            if (sComp != 0) return sComp;
            return Long.compare(o1.getId(), o2.getId());
        }
    }
}
