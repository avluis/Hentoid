package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import me.devsaki.hentoid.database.reach
import me.devsaki.hentoid.util.hash64
import java.util.Objects

@Entity
data class Chapter(
    @Id
    var id: Long = 0,
    var order: Int = -1,
    var url: String = "",
    var name: String = "",
    var uniqueId: String = "",
    var uploadDate: Long = 0
) {
    lateinit var content: ToOne<Content>

    @Backlink(to = "chapter")
    lateinit var imageFiles: ToMany<ImageFile>

    constructor(order: Int, url: String, name: String) : this(
        id = 0, order = order, url = url, name = name
    )

    constructor(chapter: Chapter) : this(
        id = chapter.id,
        order = chapter.order,
        url = chapter.url,
        name = chapter.name,
        uniqueId = chapter.uniqueId,
        uploadDate = chapter.uploadDate
    )

    // NB : Doesn't work when Content is not linked
    fun populateUniqueId() {
        val c = content.reach(this)
        if (c != null) {
            this.uniqueId = c.uniqueSiteId + "-" + order
        } else {
            this.uniqueId = order.toString()
        }
    }

    fun setContentId(contentId: Long): Chapter {
        content.targetId = contentId
        return this
    }

    fun setContent(content: Content) {
        this.content.target = content
    }

    val contentId: Long
        get() = content.targetId

    val imageList: List<ImageFile>
        get() = imageFiles.reach(this)

    val readableImageFiles: List<ImageFile>
        get() = imageList.filter(ImageFile::isReadable)

    fun setImageFiles(imageFiles: List<ImageFile>?) {
        // We do want to compare array references, not content
        if (imageFiles != null && imageFiles !== this.imageFiles) {
            this.imageFiles.clear()
            this.imageFiles.addAll(imageFiles)
        }
    }

    fun removeImageFile(img: ImageFile) {
        imageFiles.remove(img)
    }

    fun addImageFile(img: ImageFile) {
        imageFiles.add(img)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val chapter = other as Chapter
        return id == chapter.id && order == chapter.order && url == chapter.url && name == chapter.name
    }

    override fun hashCode(): Int {
        // Must be an int32, so we're bound to use Objects.hash
        return Objects.hash(id, order, url, name)
    }

    fun uniqueHash(): Long {
        return hash64("$id.$order.$url.$name".toByteArray())
    }
}