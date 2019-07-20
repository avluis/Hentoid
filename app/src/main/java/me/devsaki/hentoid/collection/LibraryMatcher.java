package me.devsaki.hentoid.collection;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import timber.log.Timber;

public class LibraryMatcher {

    private Context context;


    public LibraryMatcher(Context context) { this.context = context; }


    public List<Content> matchContentToLibrary(List<Content> list)
    {
        if (list != null && !list.isEmpty()) {
            Site site = list.get(0).getSite();

            List<String> uniqueIds = new ArrayList<>();
            for (Content c : list) {
                if (!c.getSite().equals(site)) Timber.w("Matching a list with multiple sites is currently unsupported !");
                else uniqueIds.add(c.getUniqueSiteId());
            }

            // Get matching content from library
            ObjectBoxDB db = ObjectBoxDB.getInstance(context);
            List<Content> matchedContent = db.selectContentBySourceId(site, uniqueIds);

            // Given matched content properties from their library counterpart
            //   - status (instead of default ONLINE status)
            //   - storage folder
            for (Content libContent : matchedContent) {
                for (Content content : list) {
                    if (content.getUniqueSiteId().equals(libContent.getUniqueSiteId()))
                    {
                        content.setStatus(libContent.getStatus());
                        content.setStorageFolder(libContent.getStorageFolder());
                        break;
                    }
                }
            }
        }

        return list;
    }
}
