package me.devsaki.hentoid.database.domains;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class SearchRecord {

    @Id
    public long id;
    private String searchString;

    public SearchRecord() { // Required by ObjectBox when an alternate constructor exists
    }


    public String getSearchString() {
        return searchString;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }
}
