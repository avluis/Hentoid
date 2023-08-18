package me.devsaki.hentoid.database.domains;

import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToMany;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.database.DBHelper;
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
    private long uploadDate = 0;


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

    // NB : Doesn't work when Content is not linked
    public void populateUniqueId() {
        this.uniqueId = content.getTarget().getUniqueSiteId() + "-" + order;
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

    public List<ImageFile> getImageList() {
        return (imageFiles != null && !DBHelper.isDetached(this)) ? imageFiles : Collections.emptyList();
    }

    public List<ImageFile> getReadableImageFiles() {
        if (null == imageFiles) return Collections.emptyList();
        return Stream.of(imageFiles).filter(ImageFile::isReadable).toList();
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

    public long getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(long uploadDate) {
        this.uploadDate = uploadDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chapter chapter = (Chapter) o;
        return getId() == chapter.getId() &&
                Objects.equals(getOrder(), chapter.getOrder())
                && Objects.equals(getUrl(), chapter.getUrl())
                && Objects.equals(getName(), chapter.getName());
    }

    @Override
    public int hashCode() {
        // Must be an int32, so we're bound to use Objects.hash
        return Objects.hash(getId(), getOrder(), getUrl(), getName());
    }

    public long uniqueHash() {
        return Helper.hash64((id + "." + order + "." + url + "." + name).getBytes());
    }
}
