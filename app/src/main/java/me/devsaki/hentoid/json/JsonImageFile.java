package me.devsaki.hentoid.json;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;

import java.util.List;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;

class JsonImageFile {

    private Integer order;
    private String url;
    private String pageUrl;
    private String name;
    private boolean isCover;
    private boolean favourite;
    private boolean isRead;
    private StatusContent status;
    private String mimeType;
    private long pHash;

    private boolean isTransformed;

    private int chapterOrder = -1;

    private JsonImageFile() {
    }

    static JsonImageFile fromEntity(ImageFile f) {
        JsonImageFile result = new JsonImageFile();
        result.order = f.getOrder();
        result.url = f.getUrl();
        result.pageUrl = f.getPageUrl();
        result.name = f.getName();
        result.status = f.getStatus();
        result.isCover = f.isCover();
        result.favourite = f.isFavourite();
        result.isRead = f.isRead();
        result.mimeType = f.getMimeType();
        result.pHash = f.getImageHash();
        result.isTransformed = f.isTransformed();
        if (f.getLinkedChapter() != null)
            result.chapterOrder = f.getLinkedChapter().getOrder();
        return result;
    }

    ImageFile toEntity(@NonNull List<Chapter> chapters) {
        ImageFile result = ImageFile.fromImageUrl(order, url, status, name);
        if (url.isEmpty()) result = ImageFile.fromPageUrl(order, pageUrl, status, name);
        result.setName(name);
        result.setIsCover(isCover);
        result.setFavourite(favourite);
        result.setRead(isRead);
        result.setMimeType(mimeType);
        result.setImageHash(pHash);
        result.setTransformed(isTransformed);

        if (!chapters.isEmpty() && chapterOrder > -1) {
            Optional<Chapter> chapter = Stream.of(chapters).filter(c -> c.getOrder().equals(chapterOrder)).findFirst();
            if (chapter.isPresent()) result.setChapter(chapter.get());
        }

        return result;
    }
}
